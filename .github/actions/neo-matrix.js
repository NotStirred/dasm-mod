import xml from "fast-xml-parser"

const response = await fetch("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
if (!response.ok) {
    throw new Error(`Neoforge versions response status: ${response.status}`);
}

const parser = new xml.XMLParser();
let data = parser.parse(await response.text());

console.log(data)

const output = data.metadata.versioning.versions
    .map(node => node.version)
    .filter(version => !version.includes("w")) // filter out weird snapshot versions like 0.25w14craftmine.3-beta

console.log(output)
