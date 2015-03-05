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

(def packer :edn)

(let [{:keys [ch-recv send-fn ajax-post-fn ajax-get-or-ws-handshake-fn connected-uids]}
      (sente/make-channel-socket! server-adapter {:packer packer})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv) ;; Channel sockets recieve channel
  (def chsk-send! send-fn) ;; Channel sockets send API fn
  (def connected-uids connected-uids) ;; Watchable read-only atom.
  )

(def ping-counts (atom 0))

(defmulti event-msg-handler :id)
(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (infof "Event %s" event)
  (event-msg-handler ev-msg))

(do
  (defmethod event-msg-handler :chsk/ws-ping
    [_]
    (swap! ping-counts inc))

  (defmethod event-msg-handler :default
    [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
    (let [session (:session ring-req)
          uid     (:uid     session)]
      (infof "Unhandled event: %s" event)
      (when ?reply-fn
        (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))
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
                           (assoc-in [:security :anti-forgery]
                                     {:read-token (fn [req] (-> req :params :csrf-token))})
                           (assoc-in [:static :resources] "public"))]
    (-> clack-routes
        (wrap-spy)
        (wrap-defaults clack-defaults)
        )))

(defonce router_ (atom nil))

(defn stop-router! []
  (when-let [stop-f @router_]
    (do (info "Stopping Router")
        (stop-f))))

(defn start-router! []
  (stop-router!)
  (info "Starting Router...")
  (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler*)))

(defonce web-server_ (atom nil))

(defn stop-web-server! [] (when-let [m @web-server_] ((:stop-fn m))))

(defn start-web-server! [& [port]]
  (stop-web-server!)
  (let [{:keys [stop-fn port] :as server-map} (start-web-server!* (var app) (or port 0))]
    (info (format "Server running on port `%s`" port))
    (reset! web-server_ server-map)))

(defn start! []
  (start-router!)
  (start-web-server!))
