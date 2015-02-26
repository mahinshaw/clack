(ns clack.slack
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [cheshire.core :as json]
            [manifold.stream :as s]
            [clack.clack-util.http :refer :all]
            [clack.clack-util.doctor :refer [talk->Doctor]]))

;;; Helpers
(defn find-first [pred coll]
  (first (filter pred coll)))
;;; Helpers

(def config
  "Configuration file for clack"
  (-> "config.edn" io/resource slurp edn/read-string))

(def conn-settings (atom {}))
(def ws-connection (atom nil))

(defn self-settings []
  (:self @conn-settings))

(defn get-users []
  (:users @conn-settings))

(defn direct-chat-ids
  "returns a list of maps containing the id for direct chat channel, and the user-id it relates to."
  []
  (map (fn [{:keys [id user]}]
         {:id id :user-id user})
       (:ims @conn-settings)))

(defn user->DM
  "Take a user id and return a direct channel"
  [user]
  (find-first #(= user (:user-id %)) (direct-chat-ids)))

(defn get-user [user-id]
  (:name (find-first
          #(= user-id (:id %))
          (:users @conn-settings))))

(defn get-user-id [user-name]
  (:id (find-first
        #(= user-name (:name %))
        (:users @conn-settings))))

(defn is-clack?
  "Takes a user id and returns true if it is this bot."
  [user-id]
  (= (:bot config)
     (get-user user-id)))

(defn direct-with-user? [channel user-id]
  (some #(and
          (= channel (:id %))
          (= user-id (:user-id %)))
        (direct-chat-ids)))

(defn construct-at [txt]
  (let [at (str/replace txt #"[\<\>\:]" "")]
    (str at ":")))

(defn strip-at [txt]
  (second (str/split txt #"\>\:?\s*")))

(defn replace-at [txt]
  (let [id (str/replace txt #"[\@\:]" "")]
    (str "@" (get-user id) ":")))

(defn direct-at [user-id]
  (str "<@" (get-user user-id) ">:"))

(def test-string "<@U03PGH4PL> hello")
(def test-string2 "<@U03PGH4PL>: hello")
(defn directed-text [txt]
  (if-let [to (re-find #"\<\@\w+\>\:?" txt)]
    [(-> to construct-at replace-at) (strip-at txt)]
    [nil txt]))

(defn slack-message-map
  ([msg]
   ;; TODO: define a function that gets a default channel to respond too.
   (slack-message-map "message" msg "C03Q8VDHQ"))
  ([type msg channel]
   {:type type :id (str (swap! message-id inc)) :text (str msg) :channel channel}))

(defn send-slack-message [connection msg-map]
  (when-let [ws-client @@connection]
    (s/put! ws-client (json/generate-string msg-map))))

(defn respond-msg
  "Responds to message event type."
  [msg]
  (let [{:keys [type subtype hidden channel user text ts team]
         :as msg-map
         :or {hidden false}} msg]
    (when-not hidden
      (when-not (is-clack? user)
        ;; Handle ELIZA responses.
        ;; They should only refer to direct contact with slack, either via @clack or direct channel "D03..."
        (let [[to txt] (directed-text text)]
          (if to
            (send-slack-message
             ws-connection
             (slack-message-map type (str (direct-at user) " " (talk->Doctor txt)) channel))
            (when (direct-with-user? channel user)
              (send-slack-message
               ws-connection
               (slack-message-map type (talk->Doctor text) channel)))))))))

(defn consumer
  "Take some data, parse it and return response if necessary"
  [data]
  (let [recv (json/parse-string data true)]
    (clojure.pprint/pprint recv)
    (let [type (:type recv)]
      (case type
        "message" (respond-msg recv)
        "hello"))))

(defn set-connection []
  (do (reset! conn-settings (slack-authenticate (:token config)))
      (reset! ws-connection (get-ws-connection (:url @conn-settings)))))

(defn on-closed
  "Handler to reinitialize the connection when it closes"
  []
  (println "Reset the connection")
  (set-connection)
  (s/consume consumer @@ws-connection))

(defn setup-connection-handlers
  "Registers handlers for connection."
  [conn consumer on-closed]
  (s/consume consumer @conn)
  (s/on-closed @conn on-closed))

(defn start-client []
  (do
    (set-connection)
    (setup-connection-handlers @ws-connection consumer on-closed)))

(defn stop-client
  "Sets the on-close event to nil, and closes the connection."
  []
  (loop []
    (println "Trying to close")
    (s/close! @@ws-connection)
    (if-not (s/closed? @@ws-connection)
      (do
        (println "Still not closed.")
        (recur))
      (println "Closed."))))

