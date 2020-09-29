//fileDragDrop.js を使用するにあたっての注意点
//(2016.11現在：sendTransfer、manageDefineImage にて使用）
//・dispForm.fileUploadAreaの名前でObjectが存在すること
//・dropAreaの名前でObjectが存在すること
//・dispForm.fileUploadAreaTypeの名前でObjectが存在すること（値：0=form直下、1=inline）

var dragging;
var countOver;
var checkPoint;
var isChecking;

function initVals() {
    dragging = false;
    countOver = 0;
    checkPoint = 0;
    isChecking = false;
}
;
initVals();

var stopDnDEvent = function (evt) {
    evt.stopPropagation();
    evt.preventDefault();
};

$(function () {

    document.body.addEventListener('dragover', function (evt) {
        var isLeft = function () {
            if (countOver > checkPoint) {
                checkPoint = countOver;
                t = setTimeout(isLeft, 300);
            } else {
                clearTimeout(t);
                initVals();
                $('#dropArea').hide();
                $('#dropAreaNote').show();
                dragging = false;
                isChecking = false;
            }
        };
        stopDnDEvent(evt);
        countOver++;
        evt.dataTransfer.dropEffect = 'none';


        if (dragging === false) {
            dragging = true;
            var box = document.getElementById('dropArea');
            var elm = document.getElementById('dispForm:fileUploadArea');
            var type = document.getElementById('dispForm:fileUploadAreaType');

            var _top = 0; 
            var _left = 0;
            if (type !==null && type.value === '0') {
                _top = elm.offsetTop;
                _left = elm.offsetLeft;  
            }
            else if (type !==null && type.value === '1') {
                _top = jQuery("#dispForm\\:fileUploadArea").offset().top;
                _left = jQuery("#dispForm\\:fileUploadArea").offset().left;
            }
            
            if (elm !== null && box !== null && _top !== null && _left !== null) {
                box.style.top = _top.toString() + "px";
                box.style.left = _left.toString() + "px";
                box.style.width = elm.offsetWidth.toString() + "px";
                box.style.height = elm.offsetHeight.toString() + "px";
                box.style.lineHeight = elm.offsetHeight.toString() + "px";
            }

            $('#dropAreaNote').hide();
            $('#dropArea').show();
        }
        if (!isChecking) {
            isLeft();
            isChecking = true;
        }
    }, false);

    document.getElementById('dispForm:fileUploadArea').addEventListener('dragover', function (evt) {
        stopDnDEvent(evt);
        countOver++;
        evt.dataTransfer.dropEffect = 'copy';
    }, true);
    
    window.addEventListener('dragenter', function (event) {
        event.preventDefault();
    }, false);

    window.addEventListener('drop', function (event) {
        event.preventDefault();
    }, false);

});

//UploadFileの背景色を変更
function chgUpldFileBackground(args) {
    //FileUploadエレメントと背景色を取得
    var elements = document.getElementsByClassName("ui-fileupload-content");
    if (elements.length > 0) {
        if (args.fileError) {
            elements[0].style.background = "pink";
        } else {
            elements[0].style.background = "";
        }
    }
}
;
