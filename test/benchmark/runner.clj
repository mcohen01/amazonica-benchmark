(ns benchmark.runner
  (:require [clojure.string :as str]
            [rotary.client :as rotary])
  (:import clojure.lang.Reflector
           java.util.HashMap
           benchmark.JavaDynamoClient
           com.amazonaws.services.dynamodb.AmazonDynamoDBClient
           [com.amazonaws.auth
             AWSCredentials
             BasicAWSCredentials]
           [com.amazonaws.regions
             Region
             Regions])
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
  
  (f)
  
  (delete-table cred :table-name table-name)
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

(def java-client 
  (let [aws-creds (BasicAWSCredentials.
                    (:access-key cred)
                    (:secret-key cred))
        client    (Reflector/invokeConstructor
                    AmazonDynamoDBClient
                    (into-array [aws-creds]))]
    (when-let [endpoint (:endpoint cred)]
      (->> (-> (str/upper-case endpoint)
               (.replaceAll "-" "_"))
           Regions/valueOf
           Region/getRegion
           (.setRegion client)))
    client))


(defn put-java
  [x]
  (JavaDynamoClient/putItem
    java-client
    table-name
    (java.util.HashMap. 
      {"id" (str "foo" x)
       "text" (str "barbaz" x)})))

(defn get-java [x]
  (JavaDynamoClient/getItem
    java-client
    table-name
    (str x)))


(defn execute [f]
  (let [tt    (System/nanoTime)
        raw   (int-array sample-size)
        times (make-array Object sample-size)]
    (dotimes [x sample-size]
      (let [t (System/nanoTime)]
        (f x)
        (aset-int raw x (double (/ (- (System/nanoTime) t) 1000000)))
        (aset times x (str "[" x "," (aget raw x) "]"))))
    (println "total time:"
      (/ (double (- (System/nanoTime) tt)) 1000000))
    (println "avg sample latency:" 
      (double (/ (/ (apply + (seq raw)) sample-size) 1)))
    times))

(def test-functions [#'put-rotary #'put-az #'get-rotary #'get-az #'put-java #'get-java])

(deftest benchmark []
  (doseq [f test-functions]
    (println "running " (:name (meta f)))
    (spit 
      (str (:name (meta f)))
      (apply str (interpose "," (execute @f)))))
)


(use-fixtures :once add-drop-table)



; (def csv (.split (slurp "get-az-csv") ","))

; (double (/ 
;   (reduce
;     #(+ % (Integer/valueOf %2))
;     0
;     csv)
;   (* 1000000 (count csv))))