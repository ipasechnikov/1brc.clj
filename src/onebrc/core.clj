(ns onebrc.core
  (:require [clojure.string :as str])
  (:import onebrc.java.ByteArrayToResultMap
           onebrc.java.ChunkedFile
           onebrc.java.ChunkReader
           onebrc.java.Result)
  (:gen-class))


(defn process-chunk
  [^java.nio.ByteBuffer chunk]
  (let [results (ByteArrayToResultMap.)
        chunk-reader (ChunkReader.)]
    (while (.hasRemaining chunk)
      (let [name (.readName chunk-reader chunk)
            temp (.readTemp chunk-reader chunk)]
        (.upsert results name temp)))
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
  [file-path chunk-size]
  (with-open [chunked-file (ChunkedFile. file-path chunk-size)]
    (->> (.getAllChunks chunked-file)
         (pmap process-chunk)
         (merge-and-sort))))

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
         worker-results (run-workers file-path chunk-size)
         actual-results (results->string worker-results)
         expect-results (str/trim-newline (slurp "../1brc.data/measurements-1000000000.out"))]
    ;;  (println "Expect:" expect-results)
    ;;  (println "Actual:" actual-results)
     (println "Results match:" (= actual-results expect-results))))
  (shutdown-agents))
