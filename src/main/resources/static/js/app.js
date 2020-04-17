var appctx = $("meta[name='appctx']").attr("content");

// init with empty jQuery object
window.prevSelect = $();

// Catch any bubbling focusin events (focus does not bubble)
$( ".lipids-input-row" ).click(function() {
    // Save the previously clicked value for later
    window.prevSelect = $(this);
});

function addLipidNameInputRow() {
    var newIndex = Math.max(0, $("#lipidsInput").children().length);
    var lipidNameInputRow =
            "<div class=\"form-group lipids-input-row\">" +
            "<div>" +
            "<input id=\"lipidNames" + newIndex + "\" name=\"lipidNames[" + newIndex + "]\" type=\"text\" class=\"form-control form-control-sm\" required=\"true\" ></input>" +
            "<div class=\"help-block invalid-feedback\"></div>" +
            "</div>" +
            "</div>";
    $("#lipidsInput").append(lipidNameInputRow);
}
function deleteLipidNameInputRow() {
    var nChildren = $("#lipidsInput").children().length;
    if(nChildren>1) {
        // get focused input row
        var prevSelect = $(window.prevSelect);
        var lastElement = $("#lipidsInput").children().last();
        if (prevSelect.length === 0) {
            lastElement.remove();
        } else {
            prevSelect.remove();
            window.prevSelect = $();
        }
    }
}
function clearLipidNameInputRows() {
    var nChildren = $("#lipidsInput").children().length;
    for (var i = 0; i < nChildren; i++) {
        $("#lipidsInput").children().last().remove();
    }
}

function addLipidName(name) {
    var newIndex = Math.max(0, $("#lipidsInput").children().length);
    var lipidNameInputRow =
            "<div class=\"form-group\">" +
            "<div>" +
            "<input id=\"lipidNames" + newIndex + "\" name=\"lipidNames[" + newIndex + "]\" type=\"text\" class=\"form-control form-control-sm\" required=\"true\" ></input>" +
            "<div class=\"help-block invalid-feedback\"></div>" +
            "</div>" +
            "</div>";
    $("#lipidsInput").append(lipidNameInputRow);
    $("#lipidNames"+newIndex).val(name);
}

//function findByName(lipidName) {
//    $('div').filter(function(){ 
//        return $(this).text().toLowerCase().indexOf( lipidName.toLowerCase() ) >= 0;
//    });
//}
