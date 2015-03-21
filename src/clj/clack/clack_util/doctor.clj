(ns clack.clack-util.doctor
  (:require [eliza-clj.engine :refer :all]))

(def engine-map (atom {}))

(def e (create-engine))

(defn new-engine [user]
  (do
    (swap! engine-map assoc user (create-engine))
    (get @engine-map user)))

(defn get-engine [user]
  (if-let [eng (get @engine-map user)]
    eng
    (new-engine user)))

(defn users-doctor
  [user msg]
  (-> user get-engine (process-input msg)))

(defn talk-to-doctor
  "Talk to the doctor"
  [msg]
  (process-input e msg))
