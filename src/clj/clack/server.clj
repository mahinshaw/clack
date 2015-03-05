(ns clack.server
  (:require [clack.logging :refer :all]
            [clack.websocket :refer [start-router! ring-ajax-get-or-ws-handshake ring-ajax-post]]
            [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route :refer [resources not-found]]
            [immutant.web :as web]
            [ring.middleware.defaults :refer :all]))

(refer-logging)

;;; Handler
(defroutes clack-routes
  (GET "/" [] (slurp (io/resource "public/index.html")))
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
 (POST "/chsk" req (ring-ajax-post req))
  (resources "/")
  (not-found "<h1>Nothing to see here.</h1>"))

(def app
  (let [clack-defaults (-> site-defaults
                           (assoc-in [:security :anti-forgery]
                                     {:read-token (fn [req] (-> req :params :csrf-token))})
                           (assoc-in [:static :resources] "public"))]
    (-> clack-routes
        (wrap-defaults clack-defaults))))

;;; Server
(defn start-dev-server!* [handler port]
  (info "Starting Immutant Dev...")
  (let [server (web/run-dmc handler :port port)]
    {:server server
     :port (:port server)
     :stop-fn (fn []
                (info "Stopping Immutant...")
                (web/stop server))}))

(defn start-web-server!* [handler port]
  (info "Starting Immutant...")
  (let [server (web/run handler :port port)]
    {:server server
     :port (:port server)
     :stop-fn (fn []
                (info "Stopping Immutant...")
                (web/stop server))}))

(defonce web-server_ (atom nil))

(defn stop-web-server! [] (when-let [m @web-server_] ((:stop-fn m))))

(defn start-web-server! [port dev]
  (stop-web-server!)
  (let [{:keys [stop-fn] :as server-map}
        (if dev
          (start-dev-server!* (var app) (or port 0))
          (start-web-server!* (var app) (or port 0)))]
    (info (format "Server running on port `%s`" (:port server-map)))
    (reset! web-server_ server-map)))

(defn start! [port dev]
  (start-router!)
  (start-web-server! port dev))
