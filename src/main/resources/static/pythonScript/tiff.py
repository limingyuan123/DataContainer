# plt方式的tiff可视化
import sys
from PIL import Image
import numpy as np
import matplotlib.pyplot as plt
#tiff数据的可视化

def f(path,picId):
    image_path=path
    img=Image.open(image_path)
    img=np.array(img)# 获得numpy对象, np.ndarray, RGB
    #统一使用plt进行显示，不管是plt还是cv2.imshow,在python中只认numpy.array，但是由于cv2.imread 的图片是BGR，cv2.imshow 时相应的换通道显示 
    plt.imshow(img)
    plt.axis('off')
    plt.savefig('F:\\code\\server\\snapShotCache\\'+picId+'.png')
    return True
if __name__=='__main__':
    if f(sys.argv[1],sys.argv[2]) is True:
        print('F:\\code\\server\\snapShotCache\\'+sys.argv[2]+'.png')