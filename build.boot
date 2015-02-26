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
                 [org.craigandera/eliza-clj "0.1.0"]
                 [cheshire "5.4.0"]

                 [com.taoensso/timbre "3.4.0"]
                 [prone "0.8.0"]])

(require
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
            :reload true
            :httpkit true)
     (wait))))
