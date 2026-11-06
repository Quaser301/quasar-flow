# App Settings

UML documentation for the app-wide view-setting ViewModel in
`CampBNB/app/src/main/java/com/example/campbnb/viewModels/AppViewSettings.kt`, which switches
the app between the Entrant, Campsite Owner and Admin perspectives (US 03.11.01).

```mermaid
classDiagram
    class ViewModel <<abstract>>

    class AppView {
        <<enumeration>>
        AdminView
        EntrantView
        OwnerView
    }

    class AppViewSettingController {
        -_viewSetting MutableStateFlow~AppView~
        +viewSetting StateFlow~AppView~
        +changeViewTo(newView AppView)
        +toAppViewSetting() AppViewSetting
    }

    class AppViewSetting {
        +setting StateFlow~AppView~
    }

    ViewModel <|-- AppViewSettingController
    ViewModel <|-- AppViewSetting

    AppViewSettingController *-- AppView : private flow created here, defaulted to EntrantView
    AppViewSetting o-- AppView : holds the passed-in flow without owning it
    AppViewSettingController ..> AppViewSetting : toAppViewSetting() creates one on demand
```

Reading the diagram:

- Inheritance (solid line, hollow triangle): both classes extend `ViewModel` in
  Kotlin, which is what Jetpack Compose requires to keep the setting alive across
  re-renders.
- Composition (filled diamond, `*--`): the controller creates its own private
  `MutableStateFlow` and thus owns the setting; `changeViewTo` moves the flow it
  exposes.
- Aggregation (open diamond, `o--`): `AppViewSetting` is handed the controller's
  flow, it references it but does not own it.
- Dependency (dotted arrow): `toAppViewSetting()` produces a separate read-only
  view class on demand; superviews that latch on to the returned setting re-render
  automatically when `changeViewTo` switches the setting, because the setting holds
  the `StateFlow` handle itself, never a snapshot value.
- The default is `EntrantView` because it has the least nefarious use; `OwnerView`
  and `EntrantView` mirror those users' perspectives.

## Role settings placeholders for the Settings modal

UML documentation for the per-role settings ViewModels in
`CampBNB/app/src/main/java/com/example/campbnb/viewModels/SettingsViewModels.kt`, edited from the
Settings modal while the matching view is current (US 03.11.01, paired with the Admin settings
worked separately). The settings entries themselves are placeholders chosen from what each role's
own views can do; only the structure is settled so real entries can replace them later.

```mermaid
classDiagram
    class ViewModel <<abstract>>

    class EntrantSettingsController {
        -_bookingSortSetting MutableStateFlow~String~
        -_inboxAlertsSetting MutableStateFlow~String~
        -_messageFilterSetting MutableStateFlow~String~
        +bookingSortSetting StateFlow~String~
        +inboxAlertsSetting StateFlow~String~
        +messageFilterSetting StateFlow~String~
        +changeBookingSortTo(newSort String)
        +changeInboxAlertsTo(newAlerts String)
        +changeMessageFilterTo(newFilter String)
    }

    class OwnerSettingsController {
        -_listingVisibilitySetting MutableStateFlow~String~
        -_lotteryOpenSetting MutableStateFlow~Boolean~
        -_availabilityNoticeSetting MutableStateFlow~String~
        +listingVisibilitySetting StateFlow~String~
        +lotteryOpenSetting StateFlow~Boolean~
        +availabilityNoticeSetting StateFlow~String~
        +changeListingVisibilityTo(newVisibility String)
        +changeLotteryOpenTo(open Boolean)
        +changeAvailabilityNoticeTo(newNotice String)
    }

    ViewModel <|-- EntrantSettingsController
    ViewModel <|-- OwnerSettingsController
```

Reading the diagram:

- Inheritance (solid line, hollow triangle): both controllers extend `ViewModel` so the
  settings survive re-rendering, exactly as `AppViewSettingController` does.
- Each entry follows the established private-`MutableStateFlow` plus public-`asStateFlow`
  shape: the panels latch on to the readonly flows and re-render when a `change...` method
  moves one.
- The panel composables in `ui/settings/SettingsPanels.kt` consume these plus an
  `AppViewSetting`: while their view is current they render their entries, and otherwise one
  muted inactive line. Neither panel fills its container, so both stay stackable below the
  Admin settings section, which keeps being editable regardless.

