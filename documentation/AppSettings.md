# App Settings

UML documentation for the app-wide view-setting ViewModel in
`CampBNB/app/src/main/java/com/example/campbnb/viewModels/AppViewSettings.kt`, which switches
the app between the Entrant, Campsite Owner and Admin perspectives (US 03.11.01).

```mermaid
classDiagram
    class ViewModel

    class AppView {
        <<enumeration>>
        AdminView
        EntrantView
        OwnerView
    }

    class AppViewSettingController {
        -_viewSetting MutableStateFlow~AppView~
        +viewSetting StateFlow~AppView~
        +changeViewTo(newView)
        +toAppViewSetting() AppViewSetting
    }

    class AppViewSetting {
        +setting StateFlow~AppView~
    }

    ViewModel <|-- AppViewSettingController
    ViewModel <|-- AppViewSetting

    AppViewSettingController ..> AppView : defaults to EntrantView, switched by changeViewTo
    AppViewSettingController ..> AppViewSetting : toAppViewSetting()
    AppViewSetting ..> AppView : presents the live setting via StateFlow

    note for AppViewSetting "holds the StateFlow handle itself, not a value: superviews that latch on to it re-render automatically when changeViewTo switches the setting"
    note for AppView "default is EntrantView because it has the least potential for nefarious use"
```
