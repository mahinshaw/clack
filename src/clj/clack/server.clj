(ns clack.server
  (:require [clojure.string :refer [escape]]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route :refer [resources not-found]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.resource :refer [wrap-resource]]
            [prone.middleware :refer [wrap-exceptions]]))

(defn html-escape [string]
  (str "<pre>"
       (escape string {\> "&gt;" \< "&lt;" \& "&amp;"})
       "</pre>"))

(defn format-spy [request name]
  (with-out-str
    (println (str "--------" name "--------"))
    (println)
    (pprint request)
    (println)
    (println (str "--------" name "--------"))))

(defn http-print [string name]
  (-> string (format-spy name) html-escape))

(defn handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "This be the clack trap."})

(defn wrap-spy [handler]
  (fn middleware [request]
    (let [req (http-print request "request")
          response (handler request)
          resp (http-print response "response")]
      (update-in response [:body] (fn [body] (str req body resp))))))

(defroutes clack-routes
  (not-found "<h1>Nothing to see here.</h1>"))

(def app
  (let [clack-defaults (-> site-defaults
                           (assoc-in [:static :resources] "public"))]
    (-> clack-routes
        (wrap-exceptions)
        (wrap-defaults clack-defaults))))

(def dev-app
  (-> app
      wrap-exceptions))
