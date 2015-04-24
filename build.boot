(set-env!
 :dependencies '[;; boot
                 [adzerk/boot-cljs "0.0-2814-3" :scope "test"]
                 [adzerk/boot-cljs-repl "0.1.9" :scope "test"]
                 [adzerk/boot-reload "0.2.4" :scope "test"]
                 [pandeiro/boot-http "0.6.2" :scope "test"]

                 ;; clojure
                 [org.clojure/clojure "1.7.0-beta1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [compojure "1.3.3"]
                 [ring/ring-devel "1.3.2"]
                 [ring/ring-defaults "0.1.4"]
                 [aleph "0.4.0"]
                 [org.craigandera/eliza-clj "0.1.0"]
                 [cheshire "5.4.0"]
                 [com.taoensso/timbre "3.4.0"]
                 [prone "0.8.0"]
                 [com.taoensso/sente "1.4.1"]

                 [com.cognitect/transit-clj "0.8.259"]

                 [org.immutant/web "2.0.0"]

                 ;; clojurescript
                 [org.clojure/clojurescript "0.0-2913"]
                 [reagent "0.5.0-alpha3"]
                 [com.cognitect/transit-cljs "0.8.199"]
                 ]

 :source-paths #{"src/clj" "src/cljs"}
 :resource-paths #{"resources"}
 :target-path  "target")

(require
 '[adzerk.boot-cljs :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload :refer [reload]]
 '[pandeiro.boot-http :refer [serve]]
 '[clack.slack :refer [start-client stop-client]]
 '[immutant.web :as web]
 '[clack.server :refer [start!]])

(deftask start-clack
  "Start the clack client"
  []
  (with-pre-wrap fileset
    (start-client)
    fileset))

(deftask stop-clack
  "Stop the clack client"
  []
  (with-pre-wrap fileset
    (stop-client)
    fileset))

(deftask serve-web
  "Start an immutant server"
  [p port PORT int "Optional port to serve"
   d dev? bool "Run in dev mode."]
  (let [prt (or port 3000)]
    (with-pre-wrap fileset
      (start! prt dev?)
      fileset)))

(deftask ui
  []
  (comp
   (watch)
   (speak)
   (reload)
   (cljs-repl)
   (cljs :preamble ["reagent/react.js"]
         :output-to "public/js/main.js"
         :optimizations :none
         :source-map true)
   ))

(deftask dev
  [p port PORT int "Optional port for serve"]
  (let [prt (or port 3000)]
    (comp
     (serve-web :port prt :dev? true)
     (ui))))
