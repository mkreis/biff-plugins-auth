(ns com.ractiveware.feat.home
  (:require [clj-http.client :as http]
            [com.biffweb :as biff]
            [com.ractiveware.middleware :as mid]
            [com.ractiveware.ui :as ui]
            [com.ractiveware.settings :as settings]
            [rum.core :as rum]
            [xtdb.api :as xt]))

(def email-disabled-notice
  [:.text-sm.mt-3.bg-blue-100.rounded.p-2
   "Until you add API keys for Postmark and reCAPTCHA, we'll print your sign-up "
   "link to the console. See config.edn."])

(defn signup-page [{:keys [recaptcha/site-key params] :as sys}]
  (let [invite-code (:invite params)]
    (ui/page
     (assoc sys ::ui/recaptcha true)
     (biff/form
      {:action "/auth/send-link"
       :id "signup"
       :hidden {:on-error "/"}}
      (biff/recaptcha-callback "submitSignup" "signup")
      [:.flex.flex-col.max-w-lg.gap-2.center
       [:h2.text-2xl.font-bold (str "Create a " settings/app-name " account")]
       [:.h-3]
       (if (some? invite-code)
         [:input#invite {:name "invite-code"
                         :type "hidden"
                         :value invite-code}]
         [:input#invite {:name "invite-code"
                         :type "text"
                         :placeholder "Enter your invite code"}])
       [:input#email {:name "email"
                      :type "email"
                      :autocomplete "email"
                      :placeholder "Enter your email address"}]
       [:input#password {:name "password"
                         :type "password"
                         :placeholder "(optionally) Enter a password between 8 and 64 characters."}]
       [:.w-3]
       [:button.btn.g-recaptcha
        (merge (when site-key
                 {:data-sitekey site-key
                  :data-callback "submitSignup"})
               {:type "submit"})
        "Sign up"]
       (when-some [error (:error params)]
         [:<>
          [:.h-1]
          [:.text-sm.text-red-600
           (case error
             "recaptcha" (str "You failed the recaptcha test. Try again, "
                              "and make sure you aren't blocking scripts from Google.")
             "invalid-email" "Invalid email. Try again with a different address."
             "invalid-invite" "Invite code invalid or used too many times."
             "nonconforming-password" "The password doesn't meet requirements."
             "send-failed" (str "We weren't able to send an email to that address. "
                                "If the problem persists, try another address.")
             "There was an error.")]])
       [:.h-1]
       [:.text-sm "Already have an account? " [:a.link {:href "/signin"} "Sign in"] "."]
       [:.h-3]
       biff/recaptcha-disclosure]))))

(defn link-sent [{:keys [params] :as sys}]
  (ui/page
   sys
   [:h2.text-xl.font-bold "Check your inbox"]
   [:p "We've sent a sign-in link to " [:span.font-bold (:email params)] "."]))

(defn verify-email-page [{:keys [params] :as req}]
  (ui/page
   req
   [:h2.text-2xl.font-bold (str "Sign up for " settings/app-name)]
   [:.h-3]
   (biff/form
    {:action "/auth/verify-link"
     :hidden {:token (:token params)}}
    [:div [:label {:for "email"}
           "It looks like you opened this link on a different device or browser than the one "
           "you signed up on. For verification, please enter the email you signed up with:"]]
    [:.h-3]
    [:.flex
     [:input#email {:name "email" :type "email"
                    :placeholder "Enter your email address"}]
     [:.w-3]
     [:button.btn {:type "submit"}
      "Sign in"]])
   (when-some [error (:error params)]
     [:.h-1]
     [:.text-sm.text-red-600
      (case error
        "incorrect-email" "Incorrect email address. Try again."
        "There was an error.")])))

(defn signin-page [{:keys [recaptcha/site-key params] :as sys}]
  (ui/page
   (assoc sys ::ui/recaptcha true)
   (biff/form
    {:action "/auth/send-code"
     :id "signin"
     :hidden {:on-error "/signin"}}
    (biff/recaptcha-callback "submitSignin" "signin")
    [:.flex.flex-col.max-w-lg.gap-2.center
     [:h2.text-2xl.font-bold "Sign in to " settings/app-name]
     [:.h-3]
     [:input#email {:name "email"
                    :type "email"
                    :autocomplete "email"
                    :placeholder "Enter your email address"}]
     [:input#password {:name "password"
                       :type "password"
                       :placeholder "Enter your password, or leave blank to receive a sign-in code"}]
     [:.w-3]
     [:button.btn.g-recaptcha
      (merge (when site-key
               {:data-sitekey site-key
                :data-callback "submitSignin"})
             {:type "submit"})
      "Sign in"]
     (when-some [error (:error params)]
       [:<>
        [:.h-1]
        [:.text-sm.text-red-600
         (case error
           "incorrect-password" "Incorrect email or password.  To receive a sign-in code, leave the password blank."
           "recaptcha" (str "You failed the recaptcha test. Try again, "
                            "and make sure you aren't blocking scripts from Google.")
           "invalid-email" "Invalid email. Try again with a different address."
           "send-failed" (str "We weren't able to send an email to that address. "
                              "If the problem persists, try another address.")
           "invalid-link" "Invalid or expired link. Sign in to get a new link."
           "not-signed-in" "You must be signed in to view that page."
           "There was an error.")]])
     [:.h-1]
     [:.text-sm "Don't have an account yet? " [:a.link {:href "/"} "Sign up"] "."]
     [:.h-3]
     biff/recaptcha-disclosure
     ;;email-disabled-notice
     ])))

(defn enter-code-page [{:keys [recaptcha/site-key params] :as req}]
  (ui/page
   (assoc req ::ui/recaptcha true)
   (biff/form
    {:action "/auth/verify-code"
     :id "code-form"
     :hidden {:email (:email params)}}
    (biff/recaptcha-callback "submitCode" "code-form")
    [:div [:label {:for "code"} "Enter the 6-digit code that we sent to "
           [:span.font-bold (:email params)]]]
    [:.h-1]
    [:.flex
     [:input#code {:name "code" :type "text"}]
     [:.w-3]
     [:button.btn.g-recaptcha
      (merge (when site-key
               {:data-sitekey site-key
                :data-callback "submitCode"})
             {:type "submit"})
      "Sign in"]])
   (when-some [error (:error params)]
     [:.h-1]
     [:.text-sm.text-red-600
      (case error
        "invalid-code" "Invalid code."
        "There was an error.")])
   [:.h-3]
   (biff/form
    {:action "/auth/send-code"
     :id "signin"
     :hidden {:email (:email params)
              :on-error "/signin"}}
    (biff/recaptcha-callback "submitSignin" "signin")
    [:button.link.g-recaptcha
     (merge (when site-key
              {:data-sitekey site-key
               :data-callback "submitSignin"})
            {:type "submit"})
     "Send another code"])))

(def features
  {:routes [["" {:middleware [mid/wrap-redirect-signed-in]}
             ["/"                  {:get signup-page}]]
            ["/link-sent"          {:get link-sent}]
            ["/verify-link"        {:get verify-email-page}]
            ["/signin"             {:get signin-page}]
            ["/verify-code"        {:get enter-code-page}]]})
