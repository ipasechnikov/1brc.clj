(ns onebrc.core
  (:require [clojure.string :as str])
  (:import onebrc.java.ByteArrayToResultMap
           onebrc.java.ChunkReader
           onebrc.java.Result)
  (:gen-class))

(set! *warn-on-reflection* true)

(def name-max-len 100)
(def temp-max-len 5)
(def line-max-len (+ name-max-len temp-max-len 2)) ;; +2 for newline and semicolon

(defn get-chunk
  [^java.nio.channels.FileChannel file-channel ^long chunk-size chunk-offset]
  (let [file-size (.size file-channel)]
    (assert (< chunk-offset file-size))
    (let [extended-chunk-size (+ chunk-size line-max-len)
          extended-chunk-end (+ chunk-offset extended-chunk-size)]
      (if (>= extended-chunk-end file-size)
        (let [last-chunk-size (- file-size chunk-offset)]
          (.map file-channel
                java.nio.channels.FileChannel$MapMode/READ_ONLY
                chunk-offset
                last-chunk-size))
        (let [newline-char-code 10
              extended-chunk (.map file-channel
                                   java.nio.channels.FileChannel$MapMode/READ_ONLY
                                   chunk-offset
                                   extended-chunk-size)]
          (.position extended-chunk chunk-size)
          (while (not= (.get extended-chunk) newline-char-code))
          (.flip extended-chunk))))))

(defn get-all-chunks
  [^java.nio.channels.FileChannel file-channel chunk-size]
  (let [file-size (.size file-channel)]
    (loop [all-chunks (transient [])
           chunk-offset 0]
      (if (< chunk-offset file-size)
        (let [^java.nio.MappedByteBuffer next-chunk (get-chunk file-channel chunk-size chunk-offset)]
          (recur (conj! all-chunks next-chunk)
                 (+ chunk-offset (.limit next-chunk))))
        (persistent! all-chunks)))))

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
                   (java.nio.file.Paths/get file-path (make-array String 0)))]
    (min
     (max (/ file-size number-of-threads) min-chunk-size)
     max-chunk-size)))

(defn calculate-averages
  [file-path]
  (let [chunk-size (optimal-chunk-size file-path)]
    (with-open [file-channel (java.nio.channels.FileChannel/open
                              (java.nio.file.Paths/get file-path (make-array String 0))
                              (into-array [java.nio.file.StandardOpenOption/READ]))]
      (->> (get-all-chunks file-channel chunk-size)
           (pmap process-chunk)
           (merge-and-sort)
           (results->string)))))

(defn -main
  [& args]
  (->> "measurements.txt"
       (calculate-averages)
       (println))
  (shutdown-agents))


(comment

  ;; A left-over template code for benchmarking in case I need it in the future for some reason

  (require '[criterium.core :as criterium])

  (criterium/quick-bench
   (let [measurements-txt "../1brc.data/measurements-1000000.txt"]
     (calculate-averages measurements-txt)))

  :rcf)


(comment

  ;; A quick way to check if "calculate-averages" function works correctly

  (time
   (let [measurements-txt "../1brc.data/measurements-1000000.txt"
         measurements-out "../1brc.data/measurements-1000000.out"
         actual-results (calculate-averages measurements-txt)
         expect-results (str/trim (slurp measurements-out))]
     (println "Results match:" (= actual-results expect-results))))

  :rcf)

