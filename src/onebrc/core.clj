(ns onebrc.core
  (:require [clojure.string :as str]
            [taoensso.tufte :as tufte :refer [p profile]])
  (:import onebrc.java.ByteArrayToResultMap
           onebrc.java.Result))

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

(defn request-next-chunk
  [^java.nio.channels.FileChannel file-channel chunk-offset chunk-size]
  (let [next-chunk (atom nil)]
    (locking chunk-offset
      (when-let [^java.nio.ByteBuffer chunk (get-newline-aligned-chunk file-channel @chunk-offset chunk-size)]
        (reset! next-chunk chunk)
        (swap! chunk-offset + (.limit chunk))))
    @next-chunk))

(comment
  (let [file-channel (open-file-channel "../1brc.data/measurements-10.txt")
        chunk-offset 0
        chunk-size 64
        chunk (get-newline-aligned-chunk file-channel chunk-offset chunk-size)]
    (close-file-channel file-channel)
    chunk)

  (let [file-channel (open-file-channel "../1brc.data/measurements-10.txt")
        chunk-offset (atom 0)
        chunk-size 64
        chunk (request-next-chunk file-channel chunk-offset chunk-size)]
    (close-file-channel file-channel)
    chunk)
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
        chunk-offset (atom 0)
        chunk-size 64
        chunk (request-next-chunk file-channel chunk-offset chunk-size)]
    (println (read-name chunk (java.nio.ByteBuffer/allocate name-max-length)))
    (println (read-temp chunk))
    (close-file-channel file-channel))
  :rcf)


(defn process-chunk
  [^java.nio.ByteBuffer byte-buffer ^java.nio.ByteBuffer name-buffer ^ByteArrayToResultMap results]
  (while (.hasRemaining byte-buffer)
    (read-name byte-buffer name-buffer)
    (.upsert results (.array name-buffer) (.limit name-buffer) (read-temp byte-buffer)))
  results)

(comment
  (let [file-channel (open-file-channel "../1brc.data/measurements-10.txt")
        chunk-offset (atom 0)
        chunk-size 64
        chunk (request-next-chunk file-channel chunk-offset chunk-size)
        name-buffer (java.nio.ByteBuffer/allocate name-max-length)
        results (ByteArrayToResultMap.)]
    (process-chunk chunk name-buffer results)
    (close-file-channel file-channel)
    results)
  :rcf)


(defn do-work
  [^java.nio.channels.FileChannel file-channel chunk-offset chunk-size]
  (let [name-buffer (java.nio.ByteBuffer/allocate name-max-length)
        results (ByteArrayToResultMap.)]
    (loop []
      (when-let [chunk (request-next-chunk file-channel chunk-offset chunk-size)]
        (process-chunk chunk name-buffer results)
        (recur)))
    results))

(comment
  (time
   (let [file-channel (open-file-channel "../1brc.data/measurements-10000000.txt")
         chunk-offset (atom 0)
         chunk-size (* 2 1024 1024)]
     (do-work file-channel chunk-offset chunk-size)
     (close-file-channel file-channel)))

  (profile
   {}
   (let [file-channel (open-file-channel "../1brc.data/measurements-10000000.txt")
         chunk-offset (atom 0)
         chunk-size (* 2 1024 1024)]
     (p ::do-work
        (do-work file-channel chunk-offset chunk-size))
     (close-file-channel file-channel)))
  :rcf)


(defn merge-and-sort
  [results]
  (reduce (fn [^java.util.TreeMap acc worker-results]
            (doseq [^java.util.AbstractMap$SimpleEntry entry ^java.util.List worker-results]
              (let [^String name (.getKey entry)
                    ^Result new-result (.getValue entry)]
                (if-let [^Result old-result (.get acc name)]
                  (.merge old-result new-result)
                  (.put acc name new-result))))
            acc)
          (java.util.TreeMap.)
          (map (fn [^ByteArrayToResultMap r] (.getAllResults r))
               results)))

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

(defn run-workers
  [file-path chunk-size number-of-workers]
  (let [file-channel (open-file-channel file-path)
        chunk-offset (atom 0)
        worker-futures (doall (repeatedly number-of-workers #(future (do-work file-channel chunk-offset chunk-size))))
        worker-results (doall (map #(deref %) worker-futures))
        sorted-results (merge-and-sort worker-results)
        output-string (results->string sorted-results)]
    (close-file-channel file-channel)
    output-string))

(comment
  (time
   (let [file-path "../1brc.data/measurements-1000000000.txt"
         chunk-size (* 256 1024 1024)
         number-of-workers (.availableProcessors (java.lang.Runtime/getRuntime))
         actual-results (run-workers file-path chunk-size number-of-workers)
         expect-results (str/trim-newline (slurp "../1brc.data/measurements-1000000000.out"))]
    ;;  (println "Expect:" expect-results)
    ;;  (println "Actual:" actual-results)
     (println "Results match:" (= actual-results expect-results))))
  :rcf)
