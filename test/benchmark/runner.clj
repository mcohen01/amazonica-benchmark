(ns benchmark.runner
  (:require [clojure.string :as str]
            [rotary.client :as rotary])
  (:use [clojure.test]
        [clojure.pprint]        
        [amazonica.core]
        [amazonica.aws.dynamodb]))

(def cred 
  (apply 
    hash-map 
      (interleave 
        [:access-key :secret-key :endpoint]
        (seq (.split (slurp "aws.config") " ")))))

(def table-name "TestTable")

(def sample-size 1000)

(defn add-drop-table [f]
  (create-table cred
                :table-name table-name
                :key-schema {
                  :hash-key-element {
                    :attribute-name "id"
                    :attribute-type "S"}}                  
                :provisioned-throughput {
                  :read-capacity-units 5000
                  :write-capacity-units 5000})
  
  ; wait for the tables to be created
  (doseq [table (:table-names (list-tables cred))]
    (loop [status (get-in (describe-table cred :table-name table)
                          [:table :table-status])]
      (if-not (= "ACTIVE" status)
        (do 
          (println "waiting for table" table "to be active")
          (Thread/sleep 2000)
          (recur (get-in (describe-table cred :table-name table)
                          [:table :table-status]))))))
  
  (f) ; run the test
  
  (try
    (delete-table cred :table-name table-name)
    (catch Exception e))
)

(defn put-rotary [x]
  (rotary/put-item (dissoc cred :endpoint) 
                   table-name 
                   {:id (str "foo" x)
                    :text (str "barbaz" x)}))

(defn get-rotary [x]
  (rotary/get-item (dissoc cred :endpoint)
                   table-name (str "foo" x)))

(defn put-az [x]
  (put-item cred
            :table-name table-name
            :item {
              :id (str "foo" x)
              :text (str "barbaz" x)}))

(defn get-az [x]
  (get-item cred
            :table-name table-name
            :key (str "foo" x)))

(defn execute [f]
  (let [tt (System/nanoTime)
        times (int-array sample-size)]
    (dotimes [x sample-size]
      (let [t (System/nanoTime)]
        (f x)
         (aset-int times x (- (System/nanoTime) t))))
    (println (/ (double (- (System/nanoTime) tt)) 1000000))
    (println (double (/ (/ (apply + (seq times)) sample-size) 1000000)))
    times))

(deftest benchmark []
  (println "running put-rotary")
  (spit "put-rotary.csv" (apply str (interpose "," (execute put-rotary))))

  (println "running put-amazonica")
  (spit "put-az.csv" (apply str (interpose "," (execute put-az))))

  (println "running get-rotary")
  (spit "get-rotary.csv" (apply str (interpose "," (execute get-rotary))))

  (println "running get-az")
  (spit "get-az.csv" (apply str (interpose "," (execute get-az))))
)


(use-fixtures :once add-drop-table)