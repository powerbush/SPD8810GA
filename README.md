SPD8810GA
=========
##1、
android 编译完成之后会生生一个 system.img，要想解压system.img需要知道system.img是如何打包生成的，这个好像没有工具可以查看system.img的格式，只能看到是data，所以只能一个个的试。

	:~/ext4$ file system.img
	system.img: data
试了一下unyaffs与unyaffs2都不能解压，最后才发现这个system.img是ext4打包的，用simg2img就ok了！
下载[ext4_utils](https://github.com/YuLaw/ext4-utils)源码，编译好之后会有两个文件simg2img、make_ext4fs。
##2、解压

生成 ext4格式的img文件
	
	:~/ext4$ ./simg2img system.img system.img.ext4
	computed crc32 of 0x494ace68, expected 0x00000000 

	:~/ext4$ file ./system.img.ext4 
	./system.img.ext4: Linux rev 1.0 ext4 filesystem data, UUID=57f8f4bc-abf4-655f-bf67-946fc0f9f25b (needs journal recovery) (extents) (large files)

最后一步挂载，挂载之后就可以像随意修改了，权限应该是644
	
	:~/ext4$ mkdir ./sys_dir
	:~/ext4$ sudo mount -t ext4 -o loop system.img.ext4 ./sys_dir/

##3、打包

	:~/ext4$ ./make_ext4fs -s -l 512M -a system system_new.img ./sys_dir/
	Creating filesystem with parameters:
	Size: 536870912
	Block size: 4096
	Blocks per group: 32768
	Inodes per group: 8192
	Inode size: 256
	Journal blocks: 2048
	Label: 
	Blocks: 131072
	Block groups: 4
	Reserved block group size: 31
	Created filesystem with 1465/32768 inodes and 116338/131072 blocks

	:~/ext4$  file ./system_new.img 
	./system_new.img: data
最后别忘了umount

	sudo umount sys_dir/
##4、
[原博客](http://blog.chinaunix.net/uid-26009923-id-3454597.html)中ext4_utils链接地址有误,且貌似是同时登陆两台电脑写教程(sun@ubuntu/root@yanfa3-desktop)。
