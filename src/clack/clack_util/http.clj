(ns clack.clack-util.http
  (:require [clojure.core.async :as async :refer [<! >! <!! >!! go put! take! chan]]
            [org.httpkit.client :as client]
            [cheshire.core :as json]))

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
(defn slack-client [options]
  (client/response options))

(defn slack-get [url method token msg]
  (let [c (chan)]
    (client/get (str url "/" method)
                {:query-params {:token token}}
                 #(put! c %))
    (<!! c)))

