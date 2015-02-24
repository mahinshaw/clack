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

(defn get-users []
  (:users @conn-settings))

(defn direct-chat-ids
  "returns a list of maps containing the id for direct chat channel, and the user-id it relates to."
  []
  (map (fn [{:keys [id user]}]
         {:id id :user-id user})
       (:ims @conn-settings)))

;; (defn get-user-id [user]
;;   (when-let [users (get-users)]
;;     ()))

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

(defn direct-with-user? [channel user-name]
  (= channel (-> user-name get-user-id user->DM)))

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

(defn respond-msg
  "Responds to message event type."
  [msg]
  (let [{:keys [type subtype hidden channel user text ts team]
         :as msg-map
         :or {hidden false}} msg]
    (when-not hidden
      (when (direct-with-user? channel (:bot config))
        (send-slack-message
             ws-connection
             (slack-message-map type (str (direct-at user) " " (talk->Doctor text)) channel)))
      (when-not (is-clack? user)
        (let [[to txt] (directed-text text)]
          (when to
            (send-slack-message
             ws-connection
             (slack-message-map type (str (direct-at user) " " (talk->Doctor txt)) channel))))))))

(defn consumer
  "Take some data, parse it and return response if necessary"
  [data]
  (let [recv (json/parse-string data true)]
    (clojure.pprint/pprint recv)
    (let [type (:type recv)]
      (case type
        "message" (respond-msg recv)
        "hello"))))

(defn start-client []
  (create-ws-client (:token config) ws-connection conn-settings consumer))
