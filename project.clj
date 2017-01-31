(defproject ora "0.1.0-SNAPSHOT"
  :description "audio visualization"
  :url "http://github.com/mattrepl/ora"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [tarsos-dsp/tarsos-dsp "2.4"]]
  :main ^:skip-aot ora.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})