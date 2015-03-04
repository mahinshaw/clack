(ns clack.core
  (:require-macros
   ;; for websockets
   [cljs.core.async.macros :as asyncm :refer (go go-loop)]
   )
  (:require
   [reagent.core :as r :refer [atom]]
   ;; for WebSockets
   [cljs.core.async :as async :refer (<! >! put! chan)]
   [taoensso.sente :as sente :refer (cb-success?)])
  )

(enable-console-print!)

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"
                                  {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv) ;; Receive channel
  (def chsk-send! send-fn) ;; Send Api fn
  (def chk-state state)) ;; watchable read-only atom

(def click-count (atom 0))

(defn state-ful-with-atom []
  [:div
   "I have been clicked " @click-count " times."
   [:button {:on-click #(swap! click-count inc)}
    "Inc"]])

(defn timer-component []
  (let [seconds-elapsed (atom 0)]
    (fn []
      (js/setTimeout #(swap! seconds-elapsed inc) 1000)
      [:div
       "Seconds Elapsed: " @seconds-elapsed])))

(defn by-id [id]
  (.getElementById js/document id))

(defn mountit []
  (r/render [timer-component]
            (by-id "clack-board")))

(mountit)
