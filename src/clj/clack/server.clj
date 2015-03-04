(ns clack.server
  (:require [clack.logging :refer :all]
            [clojure.string :refer [escape]]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route :refer [resources not-found]]
            [compojure.response :as resp]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.resource :refer [wrap-resource]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.immutant]
            [immutant.web :as web]
            [prone.middleware :refer [wrap-exceptions]]))

(refer-logging)

;;; Server related
(def server-adapter taoensso.sente.server-adapters.immutant/immutant-adapter)
(defn start-web-server!* [handler port]
  (info "Starting Immutant...")
  (let [server (web/run handler :port port)]
    {:server server
     :port (:port server)
     :stop-fn (fn []
                (info "Stopping Immutant...")
                (web/stop server))}))

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! server-adapter)]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv) ;; Channel sockets recieve channel
  (def chsk-send! send-fn) ;; Channel sockets send API fn
  (def connected-uids connected-uids) ;; Watchable read-only atom.
  )

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
  (GET "/" [] (slurp (io/resource "public/index.html")))
  (GET "/req" request (http-print request "request"))
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (resources "/")
  (not-found "<h1>Nothing to see here.</h1>"))

(def app
  (let [clack-defaults (-> site-defaults
                           (assoc-in [:static :resources] "public"))]
    (-> clack-routes
        (wrap-exceptions)
        (wrap-spy)
        (wrap-defaults clack-defaults))))

(def dev-app
  (-> app
      wrap-exceptions))

(defonce web-server_ (atom nil))
(defn stop-web-server! [] (when-let [m @web-server_] ((:stop-fn m))))
(defn start-web-server! [& [port]]
  (stop-web-server!)
  (let [{:keys [stop-fn port] :as server-map} (start-web-server!* (var app) (or port 0))]
    (info (format "Server running on port `%s`" port))
    (reset! web-server_ server-map)))
