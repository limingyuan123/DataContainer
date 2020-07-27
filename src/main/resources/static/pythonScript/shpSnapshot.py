import os
import sys
import geopandas as gpd
import matplotlib.pyplot as plt

# %matplotlib inline 
def execute(input,output):
    fileList = os.listdir(input)
    #挑出shp数据路径 
    for file in fileList:
        if(file.split('.')[1]== 'shp'):
            shp_file=file
            break
    plt.rcParams["font.family"] = "SimHei" # 设置全局中文字体为黑体
    # 读入中国领土面数据
    china = gpd.read_file(input+'/'+shp_file,encoding='utf-8')
    fig, ax = plt.subplots(figsize=(12, 8))
    ax = china.geometry.plot(ax=ax)
    fig.savefig(output+'.png', dpi=300)
    return True
if __name__ == '__main__':
    # print(sys.argv[1])
    if execute(sys.argv[1],sys.argv[2]) is True:
        print("ok")
 