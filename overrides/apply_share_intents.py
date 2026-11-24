from pathlib import Path
import xml.etree.ElementTree as ET

manifest = Path("AuroraPlayer/app/src/main/AndroidManifest.xml")
tree = ET.parse(manifest)
root = tree.getroot()
android = "{http://schemas.android.com/apk/res/android}"
launcher = None
for activity in root.findall(".//activity"):
    actions = [node.get(android + "name") for node in activity.findall("./intent-filter/action")]
    categories = [node.get(android + "name") for node in activity.findall("./intent-filter/category")]
    if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in categories:
        launcher = activity
        break
if launcher is None:
    raise SystemExit("Launcher activity not found; cannot register media sharing")

for mime in ("video/*", "application/octet-stream", "text/plain"):
    exists = False
    for intent_filter in launcher.findall("intent-filter"):
        actions = [node.get(android + "name") for node in intent_filter.findall("action")]
        types = [node.get(android + "mimeType") for node in intent_filter.findall("data")]
        if "android.intent.action.SEND" in actions and mime in types:
            exists = True
            break
    if not exists:
        intent_filter = ET.SubElement(launcher, "intent-filter")
        ET.SubElement(intent_filter, "action", {android + "name": "android.intent.action.SEND"})
        ET.SubElement(intent_filter, "category", {android + "name": "android.intent.category.DEFAULT"})
        ET.SubElement(intent_filter, "data", {android + "mimeType": mime})
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
tree.write(manifest, encoding="utf-8", xml_declaration=True)
