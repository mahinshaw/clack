(ns clack.slack
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [taoensso.sente :as sente]
            [compojure.core :refer :all]
            [clack.clack-util.http :refer :all]))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! {})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(def config
  "Configuration file for clack"
  (-> "config.edn" io/resource slurp edn/read-string))

(def conn-settings (atom {}))
(def ws-connection (atom nil))

(defn send-message [msg]
  (when (= nil @ws-connection)
    (create-ws-client (:token config) ws-connection conn-settings))
  (send-slack-message ws-connection msg))

(defroutes slack-routes
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req)))

