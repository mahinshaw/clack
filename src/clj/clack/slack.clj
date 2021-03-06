(ns clack.slack
  (:require [clojure.core.match :refer [match]]
            [clojure.core.async :as a :refer [chan go-loop <! put! timeout]]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clack.clack-util.doctor :refer [talk-to-doctor users-doctor]]
            [clack.slack.connection :refer :all]))

;;; Helpers
(defn find-first
  "Returns the first value in the collection that matches the predicate."
  [pred coll]
  (first (filter pred coll)))
;;; Helpers

(def cache-limit 10)

(def message-cache (atom []))

(defn add-message-to-cache
  "Adds the passed message to the cache, limiting the cache to the cache-limit."
  [msg]
  (when (<= cache-limit (count @message-cache))
    (reset! message-cache (vec (take-last (dec cache-limit) @message-cache))))
  (peek (swap! message-cache conj msg)))

(defn msg->json
  "Takes a message and returns the json string, caching the message as a map."
  [msg]
  (-> msg add-message-to-cache json/generate-string))

;;; Channels
(def recv-chan (chan))
;; buffer send channel, to mitigate rate limit issues. Start with 10.
(def send-chan (chan cache-limit))

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

(defn get-name [id key]
  (:name (find-first
          #(= id (:id %))
          (get @conn-settings key))))

(defn get-id [val val-key group-key]
  (:id (find-first
        #(= val (get % val-key))
        (get @conn-settings group-key))))

(defn get-user-name [user-id]
  (get-name user-id :users))

(defn get-user-id [user-name]
  (get-id user-name :name :users))

(defn get-chan-name [chan-id]
  (get-name chan-id :channels))

(defn is-clack?
  "Takes a user id and returns true if it is this bot."
  [user-id]
  (= (:bot config)
     (get-user-name user-id)))

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
    (str "@" (get-user-name id) ":")))

(defn direct-at-user
  "Direct the message to a user with <@{user-id}|{user-name}>"
  [user-id]
  (str "<@" user-id "|" (get-user-name user-id) ">:"))

(defn direct-at-channel
  "Direct the message to a channel with <@{chan-id}|{channel-name}>"
  [chan-id]
  (str "<@" chan-id "|" (get-chan-name chan-id) ">:"))

(def test-string "<@U03PGH4PL> hello")
(def test-string2 "<@U03PGH4PL>: hello")
(defn directed-text
  "Attempt to find directed text message, and split the message into the @<...> and message."
  [txt]
  (if-let [to (re-find #"\<\@\w+\>\:?" txt)]
    [(-> to construct-at replace-at) (strip-at txt)]
    [nil txt]))

(defn slack-message-map
  ([msg]
   ;; TODO: define a function that gets a default channel to respond too.
   (slack-message-map "message" msg "C03Q8VDHQ"))
  ([type msg channel]
   {:type type :id (str (swap! message-id inc)) :text (str msg) :channel channel}))

(defn respond-message
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
            (do
              (println "Put message")
              (put!
               send-chan
               (slack-message-map
                type
                (str (direct-at-user user) " " (users-doctor user txt))
                channel)))
            (when (direct-with-user? channel user)
              (do
                (println "Put message")
                (put!
                 send-chan
                 (slack-message-map
                  type
                  (users-doctor user text)
                  channel))))))))))

(defn consumer
  "Take some data, parse it and return response if necessary"
  [data]
  (let [recv (json/parse-string data true)]
    (clojure.pprint/pprint recv)
    (put! recv-chan recv)))

;; TODO: Is this necessary? Ideally this channel will send recieved messages upstream
;; to the dashboard etc.  But for now is it useful?
(defn start-recv-loop
  "Loop on recieved messages"
  []
  (go-loop []
    (println "Waiting to recieve message")
    (let [recv (<! recv-chan )
          type (:type recv)]
      (println "Message Recieved")
      (case type
        "message" (respond-message recv)
        "hello")
      (recur))))

(defn start-send-loop
  "Starts a loop with a 1 sec timeout, because the rtm limit is 1msg/sec."
  []
  (go-loop []
    (<! (timeout 1000))
    (let [msg (<! send-chan)]
      (send-message (msg->json msg))
      (recur))))

(defn start-client
  "Starts the websocket based client for connecting to slack."
  []
  (do
    (set-connection)
    (setup-connection-handlers consumer)
    (start-recv-loop)
    (start-send-loop)))

(defn stop-client
  []
  (stop-connection))
