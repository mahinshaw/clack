(set-env!
 :source-paths #{"src" "task"}
 :resource-paths #{"resources"}
 :dependencies '[;; boot
                 [pandeiro/boot-http "0.6.2"]
                 ;; clojure
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [compojure "1.3.2"]
                 [aleph "0.4.0-beta3"]
                 [http-kit "2.1.18"]
                 [com.taoensso/sente "1.3.0"]
                 [cheshire "5.4.0"]
                 [prone "0.8.0"]])

(require
 '[pandeiro.boot-http :refer [serve]])

(deftask dev
  [p port PORT int "Optional port for serve"]
  (let [prt (or port 3000)]
    (comp
     (serve :port prt
            :handler 'clack.server/dev-app
            :reload true
            :httpkit true)
     (wait))))
