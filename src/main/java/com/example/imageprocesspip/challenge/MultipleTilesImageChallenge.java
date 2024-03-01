package com.example.imageprocesspip.challenge;

import com.example.imageprocesspip.dao.RepositoryDao;
import com.example.imageprocesspip.entity.*;
import com.example.imageprocesspip.enums.ValidationStatus;
import com.example.imageprocesspip.service.ImageStorageService;
import com.example.imageprocesspip.utils.ImageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class MultipleTilesImageChallenge implements Challenge {

    private MultipartFile imageFile;
    private int challengeType;
    private MultiValueMap<String, String> sectionLabels;
    private int pieces;
    private ImageStorageService imageStorageService;
    private RepositoryDao repositoryDao;
    private StringRedisTemplate redisTemplate;

    private static final Logger logger = LoggerFactory.getLogger(MultipleTilesImageChallenge.class);

    // Concrete implementation for Multiple Tiles Image Selection Challenge
    public MultipleTilesImageChallenge(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }

    public MultipleTilesImageChallenge(MultipartFile imageFile, int challengeType, MultiValueMap<String, String> sectionLabels, int pieces,
                                    ImageStorageService imageStorageService, RepositoryDao repositoryDao) {
        this.imageFile = imageFile;
        this.challengeType = challengeType;
        this.sectionLabels = sectionLabels;
        this.pieces = pieces;
        this.imageStorageService = imageStorageService;
        this.repositoryDao = repositoryDao;
    }

    public MultipleTilesImageChallenge(ImageStorageService imageStorageService, RepositoryDao repositoryDao, StringRedisTemplate redisTemplate){
        this.imageStorageService = imageStorageService;
        this.repositoryDao = repositoryDao;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public ValidationStatus validate(String sessionId, String userAnswer) {

        String[] answersArray = userAnswer.split(",");
        Set<String> userAnswersSet = new LinkedHashSet<>(Arrays.asList(answersArray));
        List<String> userAnswers = new ArrayList<>(userAnswersSet);

        String correctAnswerWithComma = (String) redisTemplate.opsForValue().get(sessionId);
        if(correctAnswerWithComma == null){
            return ValidationStatus.EXPIRED;
        }
        List<String> correctAnswers = Arrays.asList(correctAnswerWithComma.split(","));
        int maxPoint = correctAnswers.size();
        int userPoint = 0;

        for (String answer : userAnswers) {
            if (correctAnswers.contains(answer)) {
                userPoint += 1;
            } else {
                userPoint -= 1;
            }
        }

        logger.info("max point: " + maxPoint);
        logger.info("user answer point: " + userPoint);
        redisTemplate.delete(sessionId);

        if( userPoint == maxPoint + 1 || userPoint == maxPoint - 1 || userPoint == maxPoint){
            return ValidationStatus.SUCCESS;
        } else {
            return ValidationStatus.FAILED;
        }
    }

    @Override
    public void createChallenge() {
        // Extract values from params and construct your HashMap
        HashMap<Integer, List<String>> sectionImageLabelMap = new HashMap<>();
        for (String key : sectionLabels.keySet()) {
            if (key.startsWith("sectionLabels[")) {
                // Extract the section number
                String sectionNumberStr = key.substring("sectionLabels[".length(), key.indexOf(']'));
                Integer sectionNumber = Integer.valueOf(sectionNumberStr);

                // Get the list of labels for this section
                List<String> labels = sectionLabels.get(key);
                sectionImageLabelMap.put(sectionNumber, labels);
            }
        }
        try {
            BufferedImage originalImage = ImageIO.read(imageFile.getInputStream());
            saveChallengeAnswer(Objects.requireNonNull(imageFile.getOriginalFilename()), originalImage, sectionImageLabelMap, pieces, challengeType);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String generateQuestionString(String label){
        return "Please select multiple images that match the label <b>'" + label + "'</b>" ;
    }

    public void saveChallengeAnswer(String filename, BufferedImage image, HashMap<Integer, List<String>> sectionImageLabelMap, int pieces, int challengeType) throws IOException {

        // Slice the image into pieces
        BufferedImage[] imageSlices = ImageUtils.sliceImagePieces(image, pieces);
        String baseName = filename.substring(0, filename.lastIndexOf('.'));
        String fileType = filename.substring(filename.lastIndexOf('.') + 1);

        // Generate a random UUID , as groupID for its pieces, also ID for the original image itself
        String originalImageUuid = UUID.randomUUID().toString().replace("-", "");

        // Save image data and get the path
        String directoryPath = "C:\\Users\\obest\\IdeaProjects\\ImageProcessPip\\testcase\\multiple\\";
        String filePath = directoryPath+filename;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        byte[] imageData = baos.toByteArray();
        imageStorageService.saveImage(filePath, imageData);
        logger.info( filePath + " save original image successful!");

        /*
        Image original = new Image()
                .setImageId(originalImageUuid)
                .setImageName(filename)
                .setImagePath(filePath)
                .setSection(-1)
                .setGroupId(originalImageUuid)
                .setIsOriginal(1);

        // Save the original image to the database
        repositoryDao.saveImages(original);
        */

        // Iterate over each image slice
        for (int i = 0; i < imageSlices.length; i++) {
            // Generate a UUID for each image slice
            String imageIdString = UUID.randomUUID().toString().replace("-", "");

            // Set the filename for each slice
            String sliceFilename = baseName + "_section_" + (i + 1) + "." + fileType;

            String slicePath = directoryPath+sliceFilename;

            ByteArrayOutputStream slicedBaos = new ByteArrayOutputStream();
            ImageIO.write(imageSlices[i], "jpg", slicedBaos);
            byte[] imageSliceData = slicedBaos.toByteArray();
            imageStorageService.saveImage(slicePath, imageSliceData);

            logger.info( slicePath + " save slice images successful!");

            Image imageSlice = new Image()
                    .setImageId(imageIdString)
                    .setImageName(sliceFilename)
                    .setImagePath(slicePath)
                    .setSection(i + 1)
                    .setGroupId(originalImageUuid)
                    .setIsOriginal(0);

            // Save the image slice to the database
            repositoryDao.saveImages(imageSlice);

            // Get labels for the current section, if there are any
            List<String> labelList = sectionImageLabelMap.getOrDefault(i+1, new ArrayList<>());

            // Save labels to the database and create relationships in image_labels table
            for (String label : labelList) {
                if(label.contains(",")){
                    List<String> words = Arrays.stream(label.split(",")).toList();
                    for( String word : words ){
                        String randomLabelIdString = UUID.randomUUID().toString().replace("-", "");
                        String actualLabelIdString = repositoryDao.saveLabelToDatabaseIfNotExists(word,randomLabelIdString); // This method saves label if it's new and returns its UUID
                        logger.info("saved label success - " + word);
                        repositoryDao.saveImageLabelRelationToDatabase(imageIdString, actualLabelIdString, challengeType);
                        logger.info("saved image label success - " + actualLabelIdString);
                        repositoryDao.saveQuestionToDatabase(actualLabelIdString,challengeType);
                        logger.info("saved question success - " + " question type : " + challengeType + " label : " + word);
                    }
                } else {
                    String randomLabelIdString = UUID.randomUUID().toString().replace("-", "");
                    String actualLabelIdString = repositoryDao.saveLabelToDatabaseIfNotExists(label,randomLabelIdString); // This method saves label if it's new and returns its UUID
                    logger.info("saved label success - " + label);
                    repositoryDao.saveImageLabelRelationToDatabase(imageIdString, actualLabelIdString, challengeType);
                    logger.info("saved image label success - " + actualLabelIdString);
                    repositoryDao.saveQuestionToDatabase(actualLabelIdString,challengeType);
                    logger.info("saved question success - " + " question type : " + challengeType + " label : " + label);
                }
            }
        }
    }

    @Override
    public CaptchaChallenge getCaptchaChallenge(int challengeType) throws IOException {

        Question question = repositoryDao.getQuestionByChallengeType(challengeType);
        Label label = repositoryDao.getLabelById(question.getLabelId());
        String questionString = this.generateQuestionString(label.getLabelName());
        List<ImageLabel> imageLabels = repositoryDao.getImageLabelsByLabelId(label.getLabelId());
        // get image list based on imageLabels, select isOriginal = 0

        Random random = new Random();
        // Generate a random index based on the size of the list ,  Get a random ImageLabel object
        int randomIndex = random.nextInt(imageLabels.size());
        ImageLabel randomImageLabel = imageLabels.get(randomIndex);
        String answerImageId = randomImageLabel.getImageId();
        List<Image> imageSlices = repositoryDao.getImageSlicesGroupById(answerImageId);
        List<String> imageSlicesIds = imageSlices.stream().map(Image::getImageId).toList();

        List<String> answerImageIds = repositoryDao.getSameGroupAnswerImages(label.getLabelId(),imageSlicesIds);
        List<String> imageSlicesBase64 = new ArrayList<>();
        List<String> temporaryIds = new ArrayList<>();
        List<String> temporaryAnswerIdList = new ArrayList<>();

        for (Image slices : imageSlices){
            byte[] imageData = imageStorageService.getImage(slices.getImagePath());
            String imageBase64 = Base64.getEncoder().encodeToString(imageData);
            String temporaryId = UUID.randomUUID().toString().replace("-", "");
            imageSlicesBase64.add(imageBase64);
            temporaryIds.add(temporaryId);
            // if this slice is one of them answer image id, then include its temporary id inside answer list
            if(answerImageIds.contains(slices.getImageId())){
                temporaryAnswerIdList.add(temporaryId);
            }
        }

        // Random UUID as session ID for user
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        String temporaryAnswerId = String.join(",", temporaryAnswerIdList);

        // Based on different challenge type, different saving method
        saveProperAnswerToRedis(sessionId,temporaryAnswerId, redisTemplate);

        CaptchaImage captchaImage = new CaptchaImage();
        captchaImage.setImageSlicesBase64String(imageSlicesBase64).setTemporaryIds(temporaryIds);

        CaptchaChallenge captchaChallenge = new CaptchaChallenge().setCaptchaImages(captchaImage)
                .setChallengeType(challengeType)
                .setSessionId(sessionId)
                .setQuestionString(questionString);

        return captchaChallenge;
    }

    private void saveProperAnswerToRedis(String sessionId, String temporaryAnswerId, StringRedisTemplate redisTemplate){
        redisTemplate.opsForValue().set(sessionId, temporaryAnswerId, 3, TimeUnit.MINUTES); // save session and answerId to redis, with ttl 3 minutes
    }

}