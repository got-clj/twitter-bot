(ns gotclj-twitter-bot.twitter
  (:use
   [twitter.oauth]
   [twitter.callbacks]
   [twitter.callbacks.handlers]
   [twitter.api.restful]
   [twitter.api.streaming])
  (:require
   [clojure.data.json :as json]
   [clojure.core.memoize :as memo]
   [clojure.core.async :refer [chan go <! >! put! <!! >!!]])
  (:import
   (twitter.callbacks.protocols SyncSingleCallback AsyncStreamingCallback)))

(def *app-consumer-key* "")
(def *app-consumer-secret* "")
(def *user-access-token* "")
(def *user-access-token-secret* "")

(def my-creds (make-oauth-creds *app-consumer-key*
                                *app-consumer-secret*
                                *user-access-token*
                                *user-access-token-secret*))

(def fetch-trusted-authors-set
  "Returns a set of friend ids"
  (memo/ttl
   (fn []
     (-> (friends-ids :oauth-creds my-creds :params {:screen-name "Gotclj"})
         :body
         :ids
         set))
   :ttl/threshold (* 60 15 1000)))

(def extract-text-and-user-id
  (juxt :text (comp :id :user)))

(defn fetch-mentions
  "Synchronous and rate limited!"
  []
  (let [trusted-authors (fetch-trusted-authors-set)]
    (->> (statuses-mentions-timeline :oauth-creds my-creds
                                     :params {:screen-name "Gotclj"})
         :body
         (map extract-text-and-user-id)
         (filter (comp trusted-authors second)))))

(def tweet-channel (chan))

(def ^:dynamic
  *custom-streaming-callback*
  "Required callback"
  (AsyncStreamingCallback.
   (fn on-body-part [response byte-stream]
     (->> byte-stream
          str
          json/read-json
          (put! tweet-channel)))
   (fn on-failure [response]
     (println response))
   (fn on-exception [response]
     (println response))))

(defn retweet [id]
  (statuses-retweet-id :params {:id id}
                       :oauth-creds my-creds))
(comment
  (when-let [msg (<!! tweet-channel)]
    (when (-> msg :user :id trusted-authors)
      (print msg)
      (retweet (:id msg))))

  (go
   (let [trusted-authors (disj (fetch-trusted-authors-set) 825653106)]
     (loop []
       (when-let [msg (<! tweet-channel)]
         (when (-> msg :user :id trusted-authors)
           (retweet (:id msg)))
         (recur)))))

  (let [worker (statuses-filter :params {:track "@gotclj"}
                                :oauth-creds my-creds
                                :callbacks *custom-streaming-callback*)]
    (Thread/sleep 60000)
    ((:cancel (meta worker))))
  )
