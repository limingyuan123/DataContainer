var testUpload = new Vue({
    el:"#app",
    data(){
        return{
            image:'',
        }
    },
    methods:{
        upLoad(){
            this.image = $('#imgShow').get(0).src;
            console.log(this.image);
            $.ajax({
                url:"/general/uploadImg",
                type:"POST",
                data:this.image,
                cache: false,
                processData: false,
                contentType: false,
                success:function (result) {
                    if (result.code == 1){
                        alert("Upload Success");
                    }else {
                        alert("Upload Failed");
                    }
                }
            })
        }
    },
    mounted(){
        $("#imgChange").click(function () {
            $("#imgFile").click();
        });
        $("#imgFile").change(function () {
            //获取input file的files文件数组;
            //$('#filed')获取的是jQuery对象，.get(0)转为原生对象;
            //这边默认只能选一个，但是存放形式仍然是数组，所以取第一个元素使用[0];
            var file = $('#imgFile').get(0).files[0];
            //创建用来读取此文件的对象
            var reader = new FileReader();
            //使用该对象读取file文件
            reader.readAsDataURL(file);
            //读取文件成功后执行的方法函数
            reader.onload = function (e) {
                //读取成功后返回的一个参数e，整个的一个进度事件
                //选择所要显示图片的img，要赋值给img的src就是e中target下result里面
                //的base64编码格式的地址
                $('#imgShow').get(0).src = e.target.result;
                $('#imgShow').show();
            }
        });
    }
})