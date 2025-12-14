
const response = await fetch("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
if (!response.ok) {
    console.error(`Neoforge versions response status: ${response.status}`);
    exit(1);
}

const parser = new DOMParser();
const data = parser.parseFromString(response.text(), "application/xml");

const errorNode = doc.querySelector("parsererror");
if (errorNode) {
    console.error("Neoforge maven returned invalid XML?!");
    exit(1);
}

const output = Array.from(xmlDoc.getElementsByTagName("version"))
    .map(node => node.childNodes[0].nodeValue)
    .filter(version => !version.includes("w")) // filter out weird snapshot versions like 0.25w14craftmine.3-beta

console.log(output)
