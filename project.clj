(defproject com.github.ipasechnikov/onebrc "0.1.0-SNAPSHOT"
  :description "1 Billion Row Challenge"
  :url "https://github.com/ipasechnikov/1brc.clj"
  :license {:name "ISC"
            :url "https://choosealicense.com/licenses/isc"
            :comment "ISC License"
            :year 2024
            :key "isc"}
  :dependencies [[org.clojure/clojure "1.11.1"]]
  :repl-options {:init-ns onebrc.core}
  :java-source-paths ["src/onebrc/java"]
  :plugins [[io.taylorwood/lein-native-image "0.3.1"]
            [lein-license "1.0.0"]]
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[criterium "0.4.6"]]}}
  :native-image {:opts ["-O3"
                        "-march=native"
                        "-H:+UnlockExperimentalVMOptions"
                        "-H:TuneInlinerExploration=1"
                        "--strict-image-heap"
                        "--initialize-at-build-time"
                        "--gc=epsilon"
                        "-H:-GenLoopSafepoints"]}
  :main onebrc.core)
