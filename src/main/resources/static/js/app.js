var appctx = $("meta[name='appctx']").attr("content");

// init with empty jQuery object
window.prevSelect = $();

// Catch any bubbling focusin events (focus does not bubble)
$( ".lipids-input-row" ).click(function() {
    // Save the previously clicked value for later
    window.prevSelect = $(this);
});

function addLipidNameInputRow() {
    var newIndex = Math.max(0, Math.min(1000, $("#lipidsInput").children().length));
    if ($("#lipidsInput").children().length >= 1000) {
        return;
    }
    var lipidNameInputRow =
            "<div class=\"form-group row lipids-input-row\">" +
            "<label class=\"col-sm-1 col-form-label col-form-label-sm\">"+(newIndex+1)+".</label>" +
            "<div class=\"col-sm-11\">" +
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
    var newIndex = Math.max(0, Math.min(1000, $("#lipidsInput").children().length));
    if ($("#lipidsInput").children().length >= 1000) {
        return;
    }
    var lipidNameInputRow =
            "<div class=\"form-group row lipids-input-row\">" +
            "<label class=\"col-sm-1 col-form-label col-form-label-sm\">"+(newIndex+1)+".</label>" +
            "<div class=\"col-sm-11\">" +
            "<input id=\"lipidNames" + newIndex + "\" name=\"lipidNames[" + newIndex + "]\" type=\"text\" class=\"form-control form-control-sm\" required=\"true\" ></input>" +
            "<div class=\"help-block invalid-feedback\"></div>" +
            "</div>" +
            "</div>";
    $("#lipidsInput").append(lipidNameInputRow);
    $("#lipidNames"+newIndex).val(name.trim());
}

//function findByName(lipidName) {
//    $('div').filter(function(){ 
//        return $(this).text().toLowerCase().indexOf( lipidName.toLowerCase() ) >= 0;
//    });
//}
