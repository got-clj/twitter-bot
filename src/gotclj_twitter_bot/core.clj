(ns gotclj-twitter-bot.core
  (:use
   [twitter.oauth]
   [twitter.callbacks]
   [twitter.callbacks.handlers]
   [twitter.api.restful]))
  (:import
   (twitter.callbacks.protocols SyncSingleCallback))

(def *app-consumer-key* "")
(def *app-consumer-secret* "")
(def *user-access-token* "")
(def *user-access-token-secret* "")

(def twitter-creds (make-oauth-creds *app-consumer-key*
                                     *app-consumer-secret*
                                     *user-access-token*
                                     *user-access-token-secret*))

(users-show :oauth-creds twitter-creds :params {:screen-name "Gotclj"})
