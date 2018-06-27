
class JS9Wrapper {
    static Load(file, opt) {
        console.log("XXXXX calling Load");
        JS9.Load(file, opt);
    }
}

window.JS9Wrapper = JS9Wrapper;


