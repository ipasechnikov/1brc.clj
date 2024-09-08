(ns onebrc.core
  (:require [clojure.string :as str]
            [taoensso.tufte :as tufte :refer [p profile]])
  (:import onebrc.java.ByteArrayToResultMap
           onebrc.java.Result)
  (:gen-class))


(set! *warn-on-reflection* false)
(tufte/add-basic-println-handler! {})


(def name-max-length 100)
(def temp-max-length 5)
(def line-max-length
  (+ name-max-length temp-max-length 1))


(defn open-file-channel
  [file-path]
  (let [path (java.nio.file.Paths/get file-path (into-array String []))
        options (into-array [java.nio.file.StandardOpenOption/READ])]
    (java.nio.channels.FileChannel/open path options)))

(defn close-file-channel
  [^java.nio.channels.FileChannel file-channel]
  (.close file-channel))

(comment
  (close-file-channel
   (open-file-channel "../1brc.data/measurements-10.txt"))
  :rcf)


(defn get-newline-aligned-chunk
  [^java.nio.channels.FileChannel file-channel ^long chunk-offset ^long chunk-size]
  (let [file-size (.size file-channel)]
    (when (< chunk-offset file-size)
      (let [chunk-size-extended (+ chunk-size line-max-length)
            chunk-end-extended (+ chunk-offset chunk-size-extended)]
        (if (>= chunk-end-extended file-size)
          (.map file-channel
                java.nio.channels.FileChannel$MapMode/READ_ONLY
                chunk-offset
                (- file-size chunk-offset))
          (let [^java.nio.MappedByteBuffer chunk (.map file-channel
                                                       java.nio.channels.FileChannel$MapMode/READ_ONLY
                                                       chunk-offset
                                                       chunk-size-extended)]
            (.position chunk chunk-size)
            (loop []
              (let [b (.get chunk)]
                (if (= b 0x0A)
                  (doto chunk
                    (.limit (.position chunk))
                    (.flip))
                  (recur))))))))))

(defn get-all-chunks
  [^java.nio.channels.FileChannel file-channel chunk-size]
  (loop [chunks (transient [])
         chunk-offset 0]
    (if-let [^java.nio.ByteBuffer next-chunk (get-newline-aligned-chunk file-channel chunk-offset chunk-size)]
      (recur (conj! chunks next-chunk)
             (+ chunk-offset (.limit next-chunk)))
      (persistent! chunks))))

(comment
  (let [file-channel (open-file-channel "../1brc.data/measurements-10.txt")
        chunk-offset 0
        chunk-size 64
        chunk (get-newline-aligned-chunk file-channel chunk-offset chunk-size)]
    (close-file-channel file-channel)
    chunk)

  (let [file-channel (open-file-channel "../1brc.data/measurements-10.txt")
        chunk-size 64
        chunks (get-all-chunks file-channel chunk-size)]
    (close-file-channel file-channel)
    chunks)
  :rcf)


(defn read-name
  [^java.nio.ByteBuffer byte-buffer ^java.nio.ByteBuffer name-buffer]
  (.clear name-buffer)
  (loop []
    (when (.hasRemaining byte-buffer)
      (let [b (.get byte-buffer)]
        (when-not (= b (byte \;))
          (.put name-buffer b)
          (recur)))))
  (.flip name-buffer))

(defn read-temp
  [^java.nio.ByteBuffer byte-buffer]
  (loop [temp 0 sign 1]
    (when (.hasRemaining byte-buffer)
      (let [b (.get byte-buffer)]
        (cond
          (= b 0x0A) (/ temp sign 10.0)
          (= b (byte \.)) (recur temp sign)
          (= b (byte \-)) (recur temp -1)
          :else (recur (+ (* temp 10) (- b (byte \0)))
                       sign))))))

(comment
  (let [name-buffer (java.nio.ByteBuffer/allocate name-max-length)]
    (read-name
     (java.nio.ByteBuffer/wrap (.getBytes "Halifax;12.9\n" java.nio.charset.StandardCharsets/UTF_8))
     name-buffer)
    (.. java.nio.charset.StandardCharsets/UTF_8
        (decode name-buffer)
        (toString)))
  ;; => "Halifax" 

  (read-temp
   (java.nio.ByteBuffer/wrap (.getBytes "12.9\n" java.nio.charset.StandardCharsets/UTF_8)))
  ;; => 12.9

  (let [byte-buffer (java.nio.ByteBuffer/wrap
                     (.getBytes "Halifax;12.9\n" java.nio.charset.StandardCharsets/UTF_8))
        name-buffer (java.nio.ByteBuffer/allocate name-max-length)]
    [(read-name byte-buffer name-buffer)
     (read-temp byte-buffer)])
  ;; => ["Halifax" 12.9]

  (let [file-channel (open-file-channel "../1brc.data/measurements-10.txt")
        chunk-size 64
        chunk (first (get-all-chunks file-channel chunk-size))]
    (println (read-name chunk (java.nio.ByteBuffer/allocate name-max-length)))
    (println (read-temp chunk))
    (close-file-channel file-channel))
  :rcf)


(defn process-chunk
  [^java.nio.ByteBuffer byte-buffer]
  (let [results (ByteArrayToResultMap.)
        name-buffer (java.nio.ByteBuffer/allocate name-max-length)]
    (while (.hasRemaining byte-buffer)
      (read-name byte-buffer name-buffer)
      (.upsert results (.array name-buffer) (.limit name-buffer) (read-temp byte-buffer)))
    results))

(comment
  (let [file-channel (open-file-channel "../1brc.data/measurements-10.txt")
        chunk-size 64
        chunk (first (get-all-chunks file-channel chunk-size))
        results (process-chunk chunk)]
    (close-file-channel file-channel)
    results)

  (time
   (let [file-channel (open-file-channel "../1brc.data/measurements-10000000.txt")
         chunk-size (* 2 1024 1024)
         chunk (first (get-all-chunks file-channel chunk-size))]
     (process-chunk chunk)
     (close-file-channel file-channel)))
  :rcf)


(defn merge-and-sort
  [results]
  (into (sorted-map)
        (persistent!
         (reduce (fn [acc ^java.util.List worker-results]
                   (reduce (fn [acc ^java.util.AbstractMap$SimpleEntry entry]
                             (let [^String name (.getKey entry)
                                   ^Result new-result (.getValue entry)]
                               (if-let [^Result old-result (get acc name)]
                                 (do
                                   (.merge old-result new-result)
                                   acc)
                                 (assoc! acc name new-result))))
                           acc
                           worker-results))
                 (transient {})
                 (map (fn [^ByteArrayToResultMap r] (.getAllResults r))
                      results)))))

(defn results->string
  [^java.util.TreeMap results]
  (let [sb (java.lang.StringBuilder.)]
    (.append sb \{)
    (doseq [[^String name ^Result result] results]
      (doto sb
        (.append name)
        (.append \=)
        (.append (.-min result))
        (.append \/)
        (.append (.mean result))
        (.append \/)
        (.append (.-max result))
        (.append ", ")))
    (doto sb
      (.setLength (- (.length sb) 2))
      (.append \}))
    (.toString sb)))

(comment
  (profile
   {}
   (let [file-path "../1brc.data/measurements-10000000.txt"
         file-channel (open-file-channel file-path)
         chunk-size (* 2 1024 1024)
         chunks (p ::get-all-chunks (doall (get-all-chunks file-channel chunk-size)))
         worker-results (mapv process-chunk chunks)
         sorted-results (merge-and-sort worker-results)
         output-string (results->string sorted-results)]
     (close-file-channel file-channel)
     (count output-string)))
  :rcf)


(defn run-workers
  [file-path chunk-size]
  (let [file-channel (open-file-channel file-path)
        chunks (get-all-chunks file-channel chunk-size)
        worker-results (pmap process-chunk chunks)
        sorted-results (merge-and-sort worker-results)
        output-string (results->string sorted-results)]
    (close-file-channel file-channel)
    output-string))

(comment
  (time
   (let [file-path "../1brc.data/measurements-1000000000.txt"
         chunk-size (* 256 1024 1024)
         actual-results (run-workers file-path chunk-size)
         expect-results (str/trim-newline (slurp "../1brc.data/measurements-1000000000.out"))]
    ;;  (println "Expect:" expect-results)
    ;;  (println "Actual:" actual-results)
     (println "Results match:" (= actual-results expect-results))))
  :rcf)


(defn -main
  [& args]
  (time
   (let [file-path "../1brc.data/measurements-1000000000.txt"
         chunk-size (* 256 1024 1024)
         actual-results (run-workers file-path chunk-size)
         expect-results (str/trim-newline (slurp "../1brc.data/measurements-1000000000.out"))]
     (println "Results match:" (= actual-results expect-results))))
  (shutdown-agents))
 