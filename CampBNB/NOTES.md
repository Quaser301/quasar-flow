# Firebase & Google Cloud
For future reference, if you need to add more cloud functions to the Cloud:
1. write your TypeScript functions in the `functions` folder. 
2. Install and authorize the Firebase CLI using:
    ```bash
    npm install -g firebase-tools
    firebase login
    ```
3. Install system packages, and build.
    ```bash
    cd functions && npm install
    npm build
    cd ..
    ```
4. Push all systems to Firebase.
    ```bash
    firebase deploy --only functions --project <FirebaseAppID>
    ```
5. DO NOT COMMIT THE FIREBASE APP ID TO THE REPO.

~ W

