(ns clack.clack-util.http
  (:require [clojure.core.async :as async :refer [<! >! <!! >!! go put! take! chan]]
            [org.httpkit.client :as client]
            [aleph.http :as http]
            [byte-streams :as bs]
            [cheshire.core :as json]
            [manifold.stream :as s]))

(defn async-get [url chan]
  (client/get url #(put! chan %)))

(defn http-get [url]
  (let [c (chan)]
    (client/get url #(put! c %))
    c))

(defn slack-post [url method token msg]
  (let [c (chan)]
    (client/post (str url "/" method)
                 {:oath-token token}
                 #(put! c %))
    (<!! c)))

;; slack rtm start requires the token in the query params,
;; https://slack/api/rtm-start?token={{token}}
;; (defn slack-client [options]
;;   (client/response options))

(defn slack-get [url method token msg]
  (let [c (chan)]
    (client/get (str url "/" method)
                {:query-params {:token token}}
                 #(put! c %))
    (<!! c)))

(defn aleph-get [url token]
  (-> @(http/get url {:query-params {:token token}})
      :body
      bs/to-string
      (json/parse-string true)))

(def id (atom 1))

(defn slack-authenticate [token]
  (aleph-get "https://slack.com/api/rtm.start" token))

(defn authenticated? [connection]
  (when (= nil @connection)
    false)
  (let [ws-client @@connection
        resp @(s/take! ws-client)
        resp-map (json/parse-string resp true)]
    (not= (:type resp-map) "error")))

(defn consumer [data]
  (println (str @data)))

(defn create-ws-client [token ws-connection conn-settings]
  (reset! conn-settings (slack-authenticate token))
  (let [ws-url (:url @conn-settings)]
    (reset! ws-connection (http/websocket-client ws-url {:insecure? false}))
    (s/consume consumer @ws-connection)
    (authenticated? ws-connection)))

(defn send-slack-message [connection msg]
  (let [ws-client @@connection]
    (s/put! ws-client (json/generate-string {:type "message" :id (str (swap! id inc)) :text (str msg) :channel "C03KHNWCJ"}))))

(defn connect-to-slack-rtm [token conn-settings]
  ((reset! conn-settings (slack-authenticate token))
   (let [ws-url (:url @conn-settings)
         ws-client @(http/websocket-client ws-url {:insecure? false})]
     (println ws-url)
     (println @(s/take! ws-client))
     (s/put! ws-client (json/generate-string {:type "message" :id (str (swap! id inc)) :text "I Like car talk too." :channel "C03KHNWCJ"}))
     (println @(s/take! ws-client))
     (s/connect ws-client ws-client))))
