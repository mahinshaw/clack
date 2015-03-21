(ns clack.clack-util.http
  (:require [aleph.http :as http]
            [byte-streams :as bs]
            [cheshire.core :as json]))

(defn http-get [url params]
  (-> @(http/get url params)
      :body
      bs/to-string
      (json/parse-string true)))

(defn slack-authenticate
  "Handle authentication with slack api."
  [token]
  (let [resp (http-get "https://slack.com/api/rtm.start" {:query-params {:token token}})]
    (if (:ok resp)
      (do
        (println "Authenticated")
        resp)
      (case (:error resp)
        ;; Handle logging and looping here.
        "migration_in_progress" (println "Migration in progress")
        "not_authed" (println "Not authorized")
        "invalid_auth" (println "Invalid Authorization")
        "account_inactive" (println "Bad account")))))

(defn get-ws-connection [url]
  "Returns a websocket client connection based on a url."
  (http/websocket-client url {:insecure? false}))
