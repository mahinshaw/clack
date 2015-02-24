(ns clack.clack-util.doctor
  (:require [eliza-clj.engine :refer :all]))

(def engine-map (atom {}))

(def e (create-engine))

(defn new-engine [user]
  (do
    (swap! @engine-map user (create-engine))
    (get @engine-map user)))

(defn get-engine [user]
  (if-let [eng (get @engine-map user)]
    eng
    (new-engine user)))

(defn user->doctor
  [user msg]
  (-> user get-engine (process-input msg)))

(defn talk->Doctor
  "Talk to the doctor"
  [msg]
  (process-input e msg))
