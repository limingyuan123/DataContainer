import sys
import matplotlib.pyplot as plt
import shapefile
##shp数据可视化，snapshot
def f(path,picId):
    shpFilePath =  path 
    listx=[]
    listy=[]
    test = shapefile.Reader(shpFilePath)
    for sr in test.shapeRecords():
        for xNew,yNew in sr.shape.points:
            listx.append(xNew)
            listy.append(yNew)
    plt.plot(listx,listy)
    plt.axis('off')
    plt.figure(figsize=(2,4))
    plt.savefig('E:\\upload\\picCache\\'+picId+'.png')
    return True
# plt.show()

if __name__ == '__main__':
    # print(sys.argv[1])
    if f(sys.argv[1],sys.argv[2]) is True:
        print('E:\\upload\\picCache\\'+sys.argv[2]+'.png')
 