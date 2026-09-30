package com.travelprice.service;
import com.travelprice.api.ApiException;
import com.travelprice.domain.*;
import com.travelprice.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
public class CommunityPhotoStorage {
    private final Path root;private final CommunityPhotoRepository photos;private final CommunityPostRepository posts;
    public CommunityPhotoStorage(@Value("${APP_UPLOAD_DIR:./uploads/community}")String root,CommunityPhotoRepository photos,CommunityPostRepository posts){this.root=Path.of(root).toAbsolutePath().normalize();this.photos=photos;this.posts=posts;}
    @Transactional public String upload(MultipartFile file,Member owner){
        if(file.isEmpty() || file.getSize()>5*1024*1024)throw new ApiException(400,"사진은 5MB 이하 JPG·PNG 파일로 올려주세요.");
        String key=UUID.randomUUID().toString();Path saved=null;
        try(var stream=ImageIO.createImageInputStream(file.getInputStream())){
            var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new ApiException(400,"이미지 파일을 확인해주세요.");
            var reader=readers.next();
            try{
                reader.setInput(stream);var format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!Set.of("jpeg","jpg","png").contains(format))throw new ApiException(400,"JPG·PNG 사진만 첨부할 수 있어요.");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width>6000 || height>6000 || (long)width*height>20000000)throw new ApiException(400,"사진 크기를 줄여주세요.");
                var image=reader.read(0);var ext=format.equals("png")?"png":"jpg";
                Files.createDirectories(root);saved=root.resolve(key+"."+ext);
                if(!ImageIO.write(image,ext,saved.toFile()))throw new IOException();
                photos.saveAndFlush(new CommunityPhoto(key,owner,ext));return key;
            }finally{reader.dispose();}
        }catch(ApiException e){throw e;}
        catch(Exception e){if(saved!=null)try{Files.deleteIfExists(saved);}catch(IOException ignored){}throw new ApiException(400,"사진을 저장하지 못했어요. 파일을 확인해주세요.");}
    }
    public void checkOwner(String key,Member member){
        if(key==null)return;
        var photo=photos.findById(key).orElseThrow(()->new ApiException(400,"첨부 사진을 다시 선택해주세요."));
        if(!photo.getOwner().getId().equals(member.getId()))throw new ApiException(403,"본인이 올린 사진만 첨부할 수 있어요.");
    }
    public void deleteUnused(String key){
        if(key==null || posts.existsByImageKey(key))return;
        photos.findById(key).ifPresent(photo->{
            var path=root.resolve(photo.getId()+"."+photo.getExtension());photos.delete(photo);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void afterCommit(){try{Files.deleteIfExists(path);}catch(IOException e){org.slf4j.LoggerFactory.getLogger(CommunityPhotoStorage.class).warn("Unused community photo could not be removed: {}",key);}}
            });
        });
    }
    @Transactional(readOnly=true) public PhotoResource read(String key,Member viewer){
        var photo=photos.findById(key).orElseThrow(()->new ApiException(404,"사진이 없습니다."));
        boolean owner=viewer!=null && (viewer.isAdmin() || photo.getOwner().getId().equals(viewer.getId()));
        if(!owner && !posts.existsByImageKeyAndHiddenFalse(key))throw new ApiException(404,"사진이 없습니다.");
        var path=root.resolve(photo.getId()+"."+photo.getExtension());if(!Files.isRegularFile(path))throw new ApiException(404,"사진이 없습니다.");
        return new PhotoResource(new FileSystemResource(path),photo.getExtension().equals("png")?"image/png":"image/jpeg");
    }
    public record PhotoResource(Resource resource,String contentType){}
}
