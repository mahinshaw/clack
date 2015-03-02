(ns clack.core
  (:require [reagent.core :as r :refer [atom]]))

(enable-console-print!)

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
