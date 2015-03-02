(set-env!
 :dependencies '[;; boot
                 [adzerk/boot-cljs "0.0-2814-3" :scope "test"]
                 [adzerk/boot-cljs-repl "0.1.9" :scope "test"]
                 [adzerk/boot-reload "0.2.4" :scope "test"]
                 [pandeiro/boot-http "0.6.2" :scope "test"]

                 ;; clojure
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [compojure "1.3.2"]
                 [aleph "0.4.0-beta3"]
                 [org.craigandera/eliza-clj "0.1.0"]
                 [cheshire "5.4.0"]
                 [com.taoensso/timbre "3.4.0"]
                 [prone "0.8.0"]

                 ;; clojurescript
                 [reagent "0.5.0-alpha3"]
                 [org.omcljs/om "0.8.8"]
                 [racehub/om-bootstrap "0.4.0"]
                 ]
 :source-paths #{"src/clj" "src/cljs"}
 :resource-paths #{"resources"}
 :target-path  "target")

(require
 '[adzerk.boot-cljs :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload :refer [reload]]
 '[pandeiro.boot-http :refer [serve]]
 '[clack.slack :refer [start-client stop-client]])

(deftask start-clack
  "Start the clack client"
  []
  (start-client))

(deftask stop-clack
  "Stop the clack client"
  []
  (stop-client))

(deftask dev
  [p port PORT int "Optional port for serve"]
  (let [prt (or port 3000)]
    (comp
     (serve :port prt
            :handler 'clack.server/dev-app
            :httpkit true)
     (watch)
     (speak)
     (reload)
     (cljs :output-to "public/js"
           :optimizations :none
           :source-map true)
     (wait))))

(deftask ui
  [p port PORT int "Optional port for serve"]
  (let [prt (or port 3000)]
    (comp
     (serve :port prt
            :dir (str (get-env :target-path) "/public")
            :httpkit true)
     (watch)
     (speak)
     (reload)
     (cljs-repl)
     (cljs :preamble ["reagent/react.js"]
           :output-to "public/js/main.js"
           :optimizations :none
           :source-map true)
     )))
