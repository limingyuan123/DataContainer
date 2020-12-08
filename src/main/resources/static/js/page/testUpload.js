var testUpload = new Vue({
    el:"#app",
    data(){
        return{
            image:'',
            uploadFiles: [],
            upLoadName:"test特殊符号",
            userId:"65",
            serverNode:"china",
            origination:"portal",
            selectValue:"1816a01c-343a-472e-027c-6390fe3eba70"
        }
    },
    methods:{
        upLoad(){
            this.image = $('#imgShow').get(0).src;
            console.log(this.image);
            $.ajax({
                url:"/uploadImg",
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
        },
        selectFile(){
            $("#uploadFile").click()
        },
        uploadChange(file, fileList) {
            console.log(fileList);
            this.uploadFiles = fileList;
        },
        submitUpload(){
            let formData = new FormData();

            // this.uploadLoading=true;

            let configContent = "<UDXZip><Name>";
            for(let index in this.uploadFiles){
                configContent+="<add value='"+this.uploadFiles[index].name+"' />";
                formData.append("datafile", this.uploadFiles[index].raw);
            }
            configContent += "</Name>";
            if(this.selectValue!=null&&this.selectValue!="none"){
                configContent+="<DataTemplate type='id'>";
                configContent+=this.selectValue;
                configContent+="</DataTemplate>"
            }
            else{
                configContent+="<DataTemplate type='none'>";
                configContent+="</DataTemplate>"
            }
            configContent+="</UDXZip>";
            // console.log(configContent)
            let configFile = new File([configContent], 'config.udxcfg', {
                type: 'text/plain',
            });
            //必填参数：name,userId,serverNode,origination,

            //test参数
            formData.append("datafile", configFile);
            formData.append("name",this.upLoadName);
            formData.append("userId",this.userId);
            formData.append("serverNode",this.serverNode);
            formData.append("origination",this.origination);
            $.ajax({
                url: "/configData",
                type:"POST",
                cache: false,
                processData: false,
                contentType: false,
                async: true,
                data:formData,
            }).done((res)=>{
                if (res.code==1){
                    let data = res.data;
                    this.$message.success('Upload success');
                }else{
                    this.$message.error('Upload failed');
                }
                console.log(res);
            }).fail((res)=>{
                this.$message.error('Upload failed');
                console.log(res);
            })
        }

    },
    mounted(){
        $("#imgChange").click(function () {
            $("#imgFile").click();
        });
        $("#imgFile").change(function () {
            var file = $('#imgFile').get(0).files[0];
            var reader = new FileReader();
            reader.readAsDataURL(file);
            reader.onload = function (e) {
                $('#imgShow').get(0).src = e.target.result;
                $('#imgShow').show();
            }
        });
    }
})