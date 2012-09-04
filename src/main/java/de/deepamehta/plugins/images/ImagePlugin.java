package de.deepamehta.plugins.images;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import de.deepamehta.core.ResultSet;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.service.listener.PluginServiceArrivedListener;
import de.deepamehta.core.service.listener.PluginServiceGoneListener;
import de.deepamehta.core.util.DeepaMehtaUtils;
import de.deepamehta.plugins.files.DirectoryListing;
import de.deepamehta.plugins.files.ResourceInfo;
import de.deepamehta.plugins.files.StoredFile;
import de.deepamehta.plugins.files.UploadedFile;
import de.deepamehta.plugins.files.service.FilesService;

/**
 * CKEditor compatible resources for image upload and browse.
 */
@Path("/images")
public class ImagePlugin extends PluginActivator implements PluginServiceArrivedListener,
        PluginServiceGoneListener {

    public static final String IMAGES = "images";

    private Logger log = Logger.getLogger(getClass().getName());

    private FilesService fileService;

    @Context
    private UriInfo uriInfo;

    /**
     * CKEditor image upload integration, see
     * CKEDITOR.config.filebrowserImageBrowseUrl
     * 
     * @param image
     *            Uploaded file resource.
     * @param func
     *            CKEDITOR function number to call.
     * @return JavaScript snippet that calls CKEditor
     */
    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public String upload(UploadedFile image, @QueryParam("CKEditorFuncNum") Long func) {
        log.info("upload image " + image.getName());
        try {
            StoredFile file = fileService.storeFile(image, IMAGES);
            // TODO introduce bean getter for file name
            String fileName = file.toJSON().getString("file_name");
            return getCkEditorCall(func, getRepoUri("/" + IMAGES + "/" + fileName), "");
        } catch (Exception e) {
            return getCkEditorCall(func, "", e.getMessage());
        }
    }

    /**
     * Returns a set of all image source URLs.
     * 
     * @return all image sources
     */
    @GET
    @Path("/browse")
    @Produces(MediaType.APPLICATION_JSON)
    public ResultSet<Image> browse() {
        log.info("browse images");
        try {
            Set<Image> images = new HashSet<Image>();
            for (JSONObject image : getImages()) {
                String path = image.getString("path");
                images.add(new Image(getRepoUri(path)));
            }
            return new ResultSet<Image>(images.size(), images);
        } catch (WebApplicationException e) { // fileService.getDirectoryListing
            throw e; // do not wrap it again
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    /**
     * Nullify file service reference.
     */
    @Override
    public void pluginServiceGone(PluginService service) {
        if (service == fileService) {
            fileService = null;
        }
    }

    /**
     * Reference the file service and create the repository path if necessary.
     */
    @Override
    public void pluginServiceArrived(PluginService service) {
        if (service instanceof FilesService) {
            log.fine("file service arrived");
            fileService = (FilesService) service;
            // TODO move the initialization to migration "0"
            try {
                // check image file repository
                ResourceInfo resourceInfo = fileService.getResourceInfo(IMAGES);
                String kind = resourceInfo.toJSON().getString("kind");
                if (kind.equals("directory") == false) {
                    String repoPath = System.getProperty("dm4.filerepo.path");
                    String message = "image storage directory " + repoPath + File.separator
                            + IMAGES + " can not be used";
                    throw new IllegalStateException(message);
                }
            } catch (WebApplicationException e) {
                // catch fileService info request error
                if (e.getResponse().getStatus() != 404) {
                    throw e;
                } else {
                    log.info("create image directory");
                    fileService.createFolder(IMAGES, "/");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Returns a in-line JavaScript snippet that calls the parent CKEditor.
     * 
     * @param func
     *            CKEDITOR function number.
     * @param uri
     *            Resource URI.
     * @param error
     *            Error message.
     * @return JavaScript snippet that calls CKEditor
     */
    private String getCkEditorCall(Long func, String uri, String error) {
        return "<script type='text/javascript'>" + "window.parent.CKEDITOR.tools.callFunction("
                + func + ", '" + uri + "', '" + error + "')" + "</script>";
    }

    /**
     * Returns the directory listing of all images.
     * 
     * @return all images
     * @throws JSONException
     */
    @SuppressWarnings("unchecked")
    private Collection<JSONObject> getImages() throws JSONException {
        DirectoryListing files = fileService.getDirectoryListing(IMAGES);
        // TODO introduce bean getter for file items
        JSONArray jsonArray = files.toJSON().getJSONArray("items");
        // TODO implement utilities support of generic type parameterized calls
        return (List<JSONObject>) DeepaMehtaUtils.toList(jsonArray);
    }

    /**
     * Returns an external accessible file repository URI of path based on
     * actual request URI.
     * 
     * @param path
     *            Relative path of a file repository resource.
     * @return URI
     */
    private String getRepoUri(String path) {
        return uriInfo.getBaseUri() + "filerepo" + path;
    }
}