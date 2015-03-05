(ns clack.core
  (:require-macros
   ;; for websockets
   [cljs.core.async.macros :as asyncm :refer (go go-loop)]
   )
  (:require
   [reagent.core :as r]
   ;; for WebSockets
   [cljs.core.async :as async :refer (<! >! put! chan)]
   [taoensso.sente :as sente :refer (cb-success?)]
   [taoensso.encore :as encore :refer [debugf infof]]
   [taoensso.sente.packers.transit :as sente-transit])
  )

(enable-console-print!)

;;; Websockets
(def packer (sente-transit/get-flexi-packer :edn))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"
                                  {:type :auto
                                   :packer packer})]
  (def chsk chsk)
  (def ch-chsk ch-recv) ;; Receive channel
  (def chsk-send! send-fn) ;; Send Api fn
  (def chk-state state)) ;; watchable read-only atom

(defmulti event-msg-handler :id)
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (infof "Event %s" event)
  (event-msg-handler ev-msg))

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event]}]
  (js/console.log "Unhandled event: %s" (pr-str event))
  (infof "Unhandled event: %s" event))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (= ?data {:first-open? true})
    (js/console.log "Channel socket successfully established!")
    (js/console.log "Channel socket state change: %s" (pr-str ?data))))

(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (js/console.log "Push event from server: %s" (pr-str ?data))
  (infof "Push event from server: %s" ?data))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (js/console.log "Handshake: %s" ?data)
    (infof "Handshake: %s" ?data)))


(def router_ (atom nil))

(defn stop-router! [] (when-let [stop-f @router_] (stop-f)))

(defn start-router! []
  (stop-router!)
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))
;;; End WebSockets

(def click-count (r/atom 0))

(defn state-ful-with-atom []
  [:div
   "I have been clicked " @click-count " times."
   [:button {:on-click #(swap! click-count inc)}
    "Inc"]])

(defn timer-component []
  (let [seconds-elapsed (r/atom 0)]
    (fn []
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div
       "Seconds Elapsed: " @seconds-elapsed])))

(defn by-id [id]
  (.getElementById js/document id))

(defn mountit []
  (r/render [timer-component]
            (by-id "clack-board")))

(start-router!)
(mountit)
