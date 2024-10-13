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
                 (map (fn [^ByteArrayToResultMap m]
                        (.getAllResults m))
                      results)))))

(defn results->string
  [results]
  (str "{"
       (str/join
        ", "
        (map (fn [[name result]]
               (str name "=" result))
             results))
       "}"))

(defn optimal-chunk-size
  [file-path]
  (let [number-of-threads (+ (.availableProcessors (Runtime/getRuntime)) 2)
        min-chunk-size (* 2 1024 1024)
        max-chunk-size (* 32 1024 1024)
        file-size (java.nio.file.Files/size
                   (java.nio.file.Paths/get file-path (into-array String [])))]
    (min
     (max (/ file-size number-of-threads) min-chunk-size)
     max-chunk-size)))

(defn calculate-averages
  [file-path]
  (let [chunk-size (optimal-chunk-size file-path)]
    (with-open [chunked-file (ChunkedFile. file-path chunk-size)]
      (->> (.getAllChunks chunked-file)
           (pmap process-chunk)
           (merge-and-sort)
           (results->string)))))

(defn -main
  [& args]
  (->> "measurements.txt"
       (calculate-averages)
       (println))
  (shutdown-agents))

;; (defn -main
;;   [& args]
;;   (time
;;    (let [file-path "../1brc.data/measurements-1000000000.txt"
;;          actual-results (calculate-averages file-path)
;;          expect-results (str/trim-newline (slurp "../1brc.data/measurements-1000000000.out"))]
;;         ;;  (println "Expect:" expect-results)
;;         ;;  (println "Actual:" actual-results)
;;      (println "Results match:" (= actual-results expect-results))))
;;   (shutdown-agents))
