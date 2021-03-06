(ns clack.websocket
  (:require [clack.logging :refer :all]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant :refer [sente-web-server-adapter]]
            [taoensso.sente.packers.transit :as sente-transit]))

(refer-logging)

(defn user-id-fn
  "Generates unique ID for request."
  [req]
  (let [uid (str (java.util.UUID/randomUUID))]
    (info "Connected: " (:remote-addr req) uid)
    uid))

(def packer (sente-transit/get-flexi-packer :edn))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! sente-web-server-adapter {:packer packer :user-id-fn user-id-fn})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv) ;; Channel sockets recieve channel
  (def chsk-send! send-fn) ;; Channel sockets send API fn
  (def connected-uids connected-uids) ;; Watchable read-only atom.
  )

(def ping-counts (atom 0))

(defmulti event-msg-handler :id)
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (infof "Event %s" event)
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :chsk/ws-ping
  [{:as ev-msg :keys [?data connected-uids]}]
  (infof "Ping Data: %s" ?data)
  (infof "Connection Count: %d" (count @connected-uids))
  (swap! ping-counts inc))

(defmethod event-msg-handler :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid     (:uid     session)]
    (infof "Unhandled event: %s" event)
    (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defonce router_ (atom nil))

(defn stop-router! []
  (when-let [stop-f @router_]
    (do (info "Stopping Router")
        (stop-f))))

(defn start-router! []
  (stop-router!)
  (info "Starting Router...")
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))
