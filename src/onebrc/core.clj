(ns onebrc.core
  (:require [clojure.string :as str])
  (:import onebrc.java.ByteArrayToResultMap
           onebrc.java.ChunkedFile
           onebrc.java.ChunkReader
           onebrc.java.Result)
  (:gen-class))


(defn do-work
  [^ChunkedFile chunked-file]
  (let [chunk-reader (ChunkReader.)
        results (ByteArrayToResultMap.)]
    (loop []
      (when-let [^java.nio.ByteBuffer chunk (.getNextChunk chunked-file)]
        (while (.hasRemaining chunk)
          (let [name (.readNameBatched chunk-reader chunk)
                temp (.readTemp chunk-reader chunk)]
            (.upsert results name temp)))
        (recur)))
    results))

(defn merge-and-sort
  [results]
  (into (sorted-map)
        (persistent!
         (reduce (fn [acc worker-results]
                   (reduce (fn [acc ^java.util.AbstractMap$SimpleEntry entry]
                             (let [name (.getKey entry)
                                   new-result (.getValue entry)]
                               (if-let [^Result old-result (get acc name)]
                                 (do
                                   (.merge old-result new-result)
                                   acc)
                                 (assoc! acc name new-result))))
                           acc
                           worker-results))
                 (transient {})
                 (map (fn [^ByteArrayToResultMap m] (.getAllResults m))
                      results)))))

(defn run-workers
  [file-path chunk-size number-of-workers]
  (with-open [chunked-file (ChunkedFile. file-path chunk-size)]
    (let [worker-futures (doall (repeatedly number-of-workers #(future (do-work chunked-file))))
          worker-results (mapv deref worker-futures)
          merged-results (merge-and-sort worker-results)]
      merged-results)))

(defn results->string
  [^java.util.TreeMap results]
  (str "{"
       (str/join
        ", "
        (map (fn [[name result]] (str name "=" result))
             results))
       "}"))

(defn -main
  [& args]
  (time
   (let [file-path "../1brc.data/measurements-1000000000.txt"
         chunk-size (* 32 1024 1024)
         number-of-workers (+ 2 (.availableProcessors (Runtime/getRuntime)))
         worker-results (run-workers file-path chunk-size number-of-workers)
         actual-results (results->string worker-results)
         expect-results (str/trim-newline (slurp "../1brc.data/measurements-1000000000.out"))]
    ;;  (println "Expect:" expect-results)
    ;;  (println "Actual:" actual-results)
     (println "Results match:" (= actual-results expect-results))))
  (shutdown-agents))
