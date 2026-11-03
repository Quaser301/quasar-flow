Campsite Reservation Application System

## AI Disclosure

| Model | Prompt | Usage |
|---|---|---|
| Qwen 3.8 Flash Next Coder IQ1_M | pull this repo: https://github.com/Quaser301/quasar-flow/tree/wyatt-6-us-031101<br>take the styling format located in the database directory and use that as the template for commenting the existent classes at viewModels/AdminViewToggle.kt<br><br>while there, also add and document a function of AppViewSettingController that converts it into a readonly class AppViewSetting, which inherits from ViewModel. Also document AppViewSetting. | Documentation/Trivial Function Implementation |
| Qwen 3.8 Flash Next Coder IQ1_M | fix that (the documentation corrections), yeah<br>Also: since the name of the class changed, modify the file name to AppViewSettings.kt to reflect the change<br>and then at the project root, make a directory labelled documentation<br>in there, make the following files:<br>Engines.puml: The UML documentation for QueryEngine&lt;T&gt; and WriteEngine&lt;T&gt;, as well as their helper classes<br>FirestoreViews.puml: The UML documentation for the FirestoreWriteView and ReadView<br>AppSettings.puml: UML documentation for the stuff we just worked on (AdminViewToggle.kt/AppViewSettings.kt)<br>hey actually need it to be mermaid, since that renders natively in markdown files on github | Documentation/UML Modelling via Mermaid |

### Note
I also used AI to populate the table, but it doesn't show up because of recursion. The prompt for those table updates is, generally speaking: "Update the AI disclosure with the same model, with the prompt as follows: "&lt;prompt>&gt;", and the usage is: &lt;usage&gt;

 ~ W
