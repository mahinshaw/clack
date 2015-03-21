(ns clack.slack.connection
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [manifold.stream :as s]
            [clack.clack-util.http :refer :all]))

(def config
  "Configuration file for clack"
  (-> "config.edn" io/resource slurp edn/read-string))

(def message-id (atom 0))

(def conn-settings (atom {}))
(def ws-connection (atom nil))

(defn set-connection []
  (do (reset! conn-settings (slack-authenticate (:token config)))
      (reset! ws-connection (get-ws-connection (:url @conn-settings)))))

(defn on-closed
  "Handler to reinitialize the connection when it closes"
  []
  (println "Reset the connection")
  (set-connection))

(defn setup-connection-handlers
  "Registers handlers for connection."
  [consumer]
  (s/consume consumer @@ws-connection)
  (s/on-closed @@ws-connection on-closed))

(defn send-message
  "Send a message in the form of a clojure map via the given connection.
  The connection should be a manifold stream."
  [msg]
  (clojure.pprint/pprint msg)
  (when-let [ws-client @@ws-connection]
    (s/put! ws-client msg)))

(defn stop-connection
  "Sets the on-close event to nil, and closes the connection."
  []
  (loop []
    (println "Trying to close")
    (s/close! @@ws-connection)
    (if-not (s/closed? @@ws-connection)
      (do
        (println "Still not closed.")
        (recur))
      (println "Closed."))))
