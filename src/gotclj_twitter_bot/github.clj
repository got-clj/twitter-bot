(ns gotclj-twitter-bot.github
  (:require [tentacles.orgs :as orgs]
            [tentacles.repos :as repos]
            [clj-time.format :as time-fmt]
            [clj-time.core :as time-core]
            [chime :refer [chime-at]]
            [clj-time.periodic :refer [periodic-seq]])
  (:import [org.joda.time DateTimeZone]))

(def github-group "got-clj")
(def day-period (* 1000 60 60 24))
(def start-time #inst "2000-01-01T20:00:00.000-00:00")

(defn starred-repos []
  (flatten (for [login-name (map :login (orgs/members github-group))]
             (repos/starring login-name))))

(defn clojure-repos [row]
  (and (= "Clojure" (:language row))
       (:updated_at row)
       (:created_at row)))

(defn rate-repo
  [{:keys [updated_at created_at]}]
  (let [created-at (time-fmt/parse created_at)
        interval-since-created (time-core/interval created-at (time-core/now))
        days-since-created (time-core/in-days interval-since-created)]
    days-since-created))

(defn find-most-interesting-repo [& [seed]]
  (->> (or seed (starred-repos))
       (filter clojure-repos)
       (sort-by rate-repo)
       (take 10)
       (shuffle)
       (first)))

(defn compose-msg
  [{:keys [name url]}]
  (let [msg (format
             "You should check out this repo %s at %s maybe next Meetup?"
             name url)]
    (if (> (count msg) 140)
      (format "check out %s" url)
      msg)))

(defn schedule-twitters []
  (chime-at
   (periodic-seq (.. (time-core/now)
                     (withZone (DateTimeZone/forID "Europe/Stockholm"))
                     (withTime 19 50 0 0))
                 (-> 1 time-core/days))
   (fn [time]
     (-> (find-most-interesting-repo)
         compose-msg))))
