async function printAll() {
    const response = await fetch("/rsc/commands.json");
    const json = await response.json();
    console.log(json);
}


