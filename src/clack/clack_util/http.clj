(ns clack.clack-util.http
  (:require [aleph.http :as http]
            [manifold.stream :as s]
            [byte-streams :as bs]
            [cheshire.core :as json]))

(defn http-get [url params]
  (-> @(http/get url params)
      :body
      bs/to-string
      (json/parse-string true)))

(def message-id (atom 1))

(defn slack-authenticate [token]
  (http-get "https://slack.com/api/rtm.start" {:query-params {:token token}}))

(defn create-ws-client [token ws-connection conn-settings consumer]
  (reset! conn-settings (slack-authenticate token))
  (when-let [ws-url (:url @conn-settings)]
    (do
      (reset! ws-connection (http/websocket-client ws-url {:insecure? false}))
      (s/consume consumer @@ws-connection))))

(defn slack-message-map
  ([msg]
   ;; TODO: define a function that gets a default channel to respond too.
   (slack-message-map "message" msg "C03Q8VDHQ"))
  ([type msg channel]
   {:type type :id (str (swap! message-id inc)) :text (str msg) :channel channel}))

(defn send-slack-message [connection msg-map]
  (when-let [ws-client @@connection]
    (s/put! ws-client (json/generate-string msg-map))))
